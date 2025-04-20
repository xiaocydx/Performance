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

import com.xiaocydx.performance.plugin.metadata.ClassData
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.Metadata.Companion.INITIAL_ID
import com.xiaocydx.performance.plugin.metadata.MethodData
import com.xiaocydx.performance.plugin.metadata.put
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.time.measureTime

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class CollectProcessor(
    private val inspector: Inspector,
    private val idGenerator: IdGenerator,
    private val output: OutputProcessor,
    private val executor:ExecutorService,
) : AbstractProcessor() {
    private val excludeClass = ConcurrentHashMap<String, ClassData>()
    private val excludeMethod = ConcurrentHashMap<String, MethodData>()
    private val mappingClass = ConcurrentHashMap<String, ClassData>()
    private val mappingMethod = ConcurrentHashMap<String, MethodData>()
    private val cacheFiles = ConcurrentHashMap.newKeySet<File>()

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
    ): CollectResult {
        val tasks = TaskCountDownLatch()

        inputJars.get().forEach { jar ->
            executor.execute(tasks) {
                val file = JarFile(jar.asFile)
                file.entries().iterator().forEach action@{ entry ->
                    if (!inspector.isClass(entry)) return@action
                    val cache = output.cacheFile(entry)?.also(cacheFiles::add)
                    inspector.excludeClassName(entry)?.let { className ->
                        return@action exclude(className, file, entry, cache)
                    }
                    collect(from = "jar", entry.name, file.getInputStream(entry), cache) { classNode ->
                        val isExcludeClass = inspector.isExcludeClass(classNode)
                        if (isExcludeClass) exclude(classNode.name, file, entry, cache)
                        isExcludeClass
                    }
                }
                file.close()
            }
        }

        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                if (!inspector.isClass(file)) return@action
                executor.execute(tasks) {
                    val entryName = inspector.entryName(directory, file)
                    val cache = output.cacheFile(entryName, file)?.also(cacheFiles::add)
                    inspector.excludeClassName(directory, file)?.let { className ->
                        return@execute exclude(className, entryName, file, cache)
                    }
                    collect(from = "directory", entryName, file.inputStream(), cache) { classNode ->
                        val isExcludeClass = inspector.isExcludeClass(classNode)
                        if (isExcludeClass) exclude(classNode.name, entryName, file, cache)
                        isExcludeClass
                    }
                }
            }
        }

        tasks.await()
        return CollectResult(excludeClass, excludeMethod, mappingClass, mappingMethod, cacheFiles)
    }

    private fun exclude(className: String, file: JarFile, entry: JarEntry, cache: File?) {
        excludeClass.put(ClassData(className))
        output.write(file, entry, cache)
        logger.debug { "exclude from jar ${entry.name}" }
    }

    private fun exclude(className: String, entryName: String, file: File, cache: File?) {
        excludeClass.put(ClassData(className))
        output.write(entryName, file, cache)
        logger.debug { "exclude from directory $entryName" }
    }

    private inline fun collect(
        from: String,
        entryName: String,
        inputStream: InputStream,
        cache: File?,
        isExcludeClass: (ClassNode) -> Boolean
    ) {
        var isCollected = false
        val time = measureTime {
            inputStream.use {
                val classReader = ClassReader(it)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)
                if (isExcludeClass(classNode)) return@use

                mappingClass.put(ClassData(classNode.name, entryName, cache, classReader, classNode))
                classNode.methods.forEach { method ->
                    if (inspector.isExcludeMethod(method)) {
                        excludeMethod.put(method.toMethodData(id = INITIAL_ID, classNode.name))
                    } else {
                        mappingMethod.put(method.toMethodData(id = idGenerator.generate(), classNode.name))
                    }
                }
                isCollected = true
            }
        }
        if (isCollected) logger.debug { "collect from $from $entryName $time" }
    }

    private fun MethodNode.toMethodData(id: Int, className: String) = run {
        MethodData(id = id, access = access, className = className, methodName = name, desc = desc)
    }
}

internal data class CollectResult(
    val excludeClass: Map<String, ClassData>,
    val excludeMethod: Map<String, MethodData>,
    val mappingClass: Map<String, ClassData>,
    val mappingMethod: Map<String, MethodData>,
    val cacheFiles: Set<File>,
)