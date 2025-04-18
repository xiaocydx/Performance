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

@file:Suppress("CanBeParameter")

package com.xiaocydx.performance.plugin.processor

import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import com.xiaocydx.performance.plugin.dispatcher.SerialDispatcher
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.writeTo
import java.io.File
import java.util.concurrent.Future
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.time.measureTime

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class OutputProcessor(
    private val outputExclude: File,
    private val outputJar: File,
    private val inspector: Inspector,
    private val excludeClassFile: String,
    private val excludeMethodFile: String,
    private val mappingMethodFile: String,
    private val dispatcher: Dispatcher,
    private val jarDispatcher: SerialDispatcher,
) : AbstractProcessor() {
    private val jarTasks = TaskCountDownLatch()
    private val jarOutput = JarOutputStream(outputJar.outputStream().buffered())
    private val logger = Logger(javaClass)

    fun writeToExclude(file: JarFile, entry: JarEntry): File? {
        // TODO: 补充R文件过滤
        if (!inspector.isWritable(entry)) return null
        val excludeFile: File
        val time = measureTime {
            val crc = entry.crc.toString(16)
            excludeFile = File(outputExclude, "${entry.name}_${crc}")
            if (!excludeFile.exists()) {
                excludeFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
                excludeFile.outputStream().use { os ->
                    file.getInputStream(entry).use { it.copyTo(os) }
                }
            }
        }
        logger.debug { "writeToExclude ${entry.name} $time" }
        return excludeFile
    }

    fun writeToJar(name: String, bytes: ByteArray) {
        jarDispatcher.execute(jarTasks) {
            val time = measureTime {
                jarOutput.putNextEntry(JarEntry(name))
                jarOutput.write(bytes)
                jarOutput.closeEntry()
            }
            logger.debug { "writeToJar $name $time" }
        }
    }

    fun writeToJar(name: String, file: File) {
        if (!inspector.isWritable(file)) return
        jarDispatcher.execute(jarTasks) {
            val time = measureTime {
                jarOutput.putNextEntry(JarEntry(name))
                file.inputStream().use { it.copyTo(jarOutput) }
                jarOutput.closeEntry()
            }
            logger.debug { "writeToJar $name $time" }
        }
    }

    fun writeToJar(file: JarFile, entry: JarEntry) {
        if (!inspector.isWritable(entry)) return
        jarDispatcher.execute(jarTasks) {
            val time = measureTime {
                jarOutput.putNextEntry(JarEntry(entry.name))
                file.getInputStream(entry).use { it.copyTo(jarOutput) }
                jarOutput.closeEntry()
            }
            logger.debug { "writeToJar ${entry.name} $time" }
        }
    }

    fun closeJarFile(file: JarFile) {
        jarDispatcher.execute(jarTasks) { file.close() }
    }

    fun writeToMapping(result: CollectResult): Future<Unit> {
        return dispatcher.submit {
            result.excludeClass.values.toList().writeTo(File(excludeClassFile))
            result.excludeMethod.values.toList().writeTo(File(excludeMethodFile))
            result.mappingMethod.values.sortedBy { it.id }.writeTo(File(mappingMethodFile))
        }
    }

    fun await() {
        jarTasks.await()
        jarOutput.close()
    }
}