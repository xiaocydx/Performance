/*
 * Copyright 2025 xiaocydx
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaocydx.performance.plugin.processor

import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.Metadata.Companion.INITIAL_ID
import com.xiaocydx.performance.plugin.metadata.MethodData
import com.xiaocydx.performance.plugin.metadata.writeTo
import com.xiaocydx.performance.plugin.output.CacheOutput
import com.xiaocydx.performance.plugin.output.JarOutput
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.time.measureTime


/**
 * @author xcc
 * @date 2025/4/13
 */
internal class OutputProcessor(
    private val inspector: Inspector,
    private val jarOutput: JarOutput,
    private val cacheOutput: CacheOutput?,
    private val excludeClassFile: String,
    private val excludeMethodFile: String,
    private val mappingMethodFile: String,
    private val mappingBaseFile: String,
    private val executor: ExecutorService
) : AbstractProcessor() {
    private val tasks = TaskCountDownLatch()
    private var scanningTask: Future<ScanningResult>? = null

    fun scanningCache() {
        val cacheDir = cacheOutput?.cacheDir ?: return
        scanningTask = executor.submit(Callable {
            val scanning = ScanningResult()
            val time = measureTime {
                val subTasks = mutableListOf<Future<ScanningResult>>()
                fun addSubTask(listFiles: Array<File>) {
                    listFiles.forEach {
                        if (it.isDirectory && (it.name == "com"
                                        || it.name == "kotlin"
                                        || it.name == "androidx")) {
                            addSubTask(it.listFiles() ?: emptyArray())
                            return@forEach
                        }
                        subTasks.add(executor.submit(Callable {
                            val result = ScanningResult()
                            it.walk().forEach { file -> result.add(file) }
                            result
                        }))
                    }
                }
                addSubTask(cacheDir.listFiles() ?: emptyArray())
                subTasks.forEach { scanning.add(it.get()) }
            }
            logger.debug { "scanningCache $time" }
            scanning
        })
    }

    fun readMappingBase(): IdGenerator {
        var initial = INITIAL_ID
        var methodKeyToId: HashMap<String, Int>? = null
        // 若mappingBaseFile为空，则读取上一次编译的mappingMethodFile
        val file = File(mappingBaseFile.ifEmpty { mappingMethodFile })
        if (file.exists()) {
            methodKeyToId = HashMap()
            val metadata = MethodData(
                id = initial, access = 0,
                className = "", methodName = "", desc = ""
            )
            file.bufferedReader().useLines { lines ->
                lines.forEach {
                    MethodData.fromOutput(it, metadata)
                    methodKeyToId[metadata.key] = metadata.id
                    if (metadata.id > initial) initial = metadata.id
                }
            }
        }
        return IdGenerator(initial, methodKeyToId)
    }

    fun cacheFile(entry: JarEntry): File? {
        return cacheOutput?.cacheFile(entry)
    }

    fun cacheFile(entryName: String, file: File): File? {
        return cacheOutput?.cacheFile(entryName, file)
    }

    fun write(entryName: String, bytes: ByteArray, cache: File?) {
        if (cacheOutput != null && cache != null) {
            cacheOutput.write(bytes, cache)
            logger.debug { "writeToCache $entryName" }
        } else {
            jarOutput.write(entryName, bytes)
            logger.debug { "writeToJar $entryName" }
        }
    }

    fun write(file: JarFile, entry: JarEntry, cache: File?) {
        if (!inspector.isWritable(entry)) return
        if (cacheOutput != null && cache != null) {
            cacheOutput.write(file, entry, cache)
            logger.debug { "writeToCache ${entry.name}" }
        } else {
            jarOutput.write(file, entry)
            logger.debug { "writeToJar ${entry.name}" }
        }
    }

    fun write(entryName: String, file: File, cache: File?) {
        if (!inspector.isWritable(file)) return
        if (cacheOutput != null && cache != null) {
            cacheOutput.write(file, cache)
            logger.debug { "writeToCache $entryName" }
        } else {
            jarOutput.write(entryName, file)
            logger.debug { "writeToJar $entryName" }
        }
    }

    fun cleanInvalidCache(collect: CollectResult) {
        val scanningTask = scanningTask ?: return
        this.scanningTask = null
        executor.execute(tasks) {
            val scanning = scanningTask.get()
            scanning.cacheFiles.forEach {
                executor.execute(tasks) {
                    if (it !in collect.cacheFiles) {
                        it.delete()
                        logger.debug { "cleanInvalidCache $it" }
                    }
                }
            }
            scanning.emptyDirectories.forEach {
                executor.execute(tasks) {
                    it.delete()
                    logger.debug { "cleanInvalidCache $it" }
                }
            }
        }
    }

    fun writeMappingMethod(collect: CollectResult) = with(collect) {
        val excludeClass = excludeClass.values
        val excludeMethod = excludeMethod.values
        val mapping = mappingMethod.values
        executor.execute(tasks) { excludeClass.writeTo(File(excludeClassFile)) }
        executor.execute(tasks) { excludeMethod.writeTo(File(excludeMethodFile)) }
        executor.execute(tasks) { mapping.sortedBy { it.id }.writeTo(File(mappingMethodFile)) }
    }

    fun await() {
        tasks.await()
        jarOutput.close()
    }

    private class ScanningResult(
        val cacheFiles: MutableList<File> = ArrayList(1000),
        val emptyDirectories: MutableList<File> = ArrayList(100)
    ) {
        fun add(result: ScanningResult) {
            cacheFiles.addAll(result.cacheFiles)
            emptyDirectories.addAll(result.emptyDirectories)
        }

        fun add(file: File) {
            if (file.isFile) {
                cacheFiles.add(file)
            } else if (file.list().isNullOrEmpty()) {
                emptyDirectories.add(file)
            }
        }
    }
}