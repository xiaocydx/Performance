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

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
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
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class CollectProcessor(
    private val dispatcher: Dispatcher,
    private val idGenerator: IdGenerator,
    private val inspector: Inspector,
    private val output: OutputProcessor,
) : AbstractProcessor() {
    private val excludeClass = ConcurrentHashMap<String, ClassData>()
    private val excludeMethod = ConcurrentHashMap<String, MethodData>()
    private val mappingClass = ConcurrentHashMap<String, ClassData>()
    private val mappingMethod = ConcurrentHashMap<String, MethodData>()

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
    ): CollectResult {
        val tasks = TaskCountDownLatch()

        inputJars.get().forEach { jar ->
            dispatcher.execute(tasks) {
                val file = JarFile(jar.asFile)
                file.entries().iterator().forEach action@{ entry ->
                    if (!inspector.isClass(entry)) return@action
                    inspector.toExcludeClass(entry)?.let {
                        excludeClass.put(ClassData(it))
                        output.write(file, entry)
                        return@action
                    }
                    val time = measureTimeMillis { collect(entry.name, file.getInputStream(entry)) }
                    println("Collect from jar ${entry.name} ${time}ms")
                }
                output.close(file)
            }
        }

        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                if (!inspector.isClass(file)) return@action
                val entryName = inspector.entryName(directory, file)
                inspector.toExcludeClass(directory, file)?.let {
                    excludeClass.put(ClassData(it))
                    output.write(entryName, file)
                    return@action
                }
                dispatcher.execute(tasks) {
                    val time = measureTimeMillis { collect(entryName, file.inputStream()) }
                    println("Collect from directory $entryName ${time}ms")
                }
            }
        }

        tasks.await()
        return CollectResult(excludeClass, excludeMethod, mappingClass, mappingMethod)
    }

    private fun collect(entryName: String, inputStream: InputStream) {
        inputStream.use {
            val classReader = ClassReader(it)
            val classNode = ClassNode()
            classReader.accept(classNode, 0)

            if (inspector.isExcludeClass(classNode)) {
                excludeClass.put(ClassData(classNode.name))
                return@use
            }

            mappingClass.put(ClassData(classNode.name, entryName, classReader, classNode))
            classNode.methods.forEach { method ->
                if (inspector.isExcludeMethod(method)) {
                    excludeMethod.put(method.toMethodData(id = INITIAL_ID, classNode.name))
                } else {
                    mappingMethod.put(method.toMethodData(id = idGenerator.generate(), classNode.name))
                }
            }
        }
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
)