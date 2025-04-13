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

package com.xiaocydx.performance.plugin.handler

import com.xiaocydx.performance.plugin.PerformanceClassVisitor
import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import groovyjarjarasm.asm.Opcodes.ASM9
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.util.concurrent.Future

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class ModifyHandler(
    private val dispatcher: Dispatcher,
    private val outputHandler: OutputHandler,
) {
    private val tasks = mutableListOf<Future<*>>()

    fun submitJars(inputJars: ListProperty<RegularFile>) {
        outputHandler.write(inputJars)
    }

    fun submitDirectories(directories: ListProperty<Directory>) {
        directories.get().forEach { directory ->
            println("handling " + directory.asFile.absolutePath)
            directory.asFile.walk().forEach { file ->
                if (!file.isFile) return@forEach

                val name = name(relativePath(directory, file))
                println("Adding from directory $name")
                if (!filter(file)) {
                    outputHandler.write(name, file)
                    return@forEach
                }

                val task = dispatcher.submit {
                    val bytes = file.inputStream().use {
                        val classReader = ClassReader(it)
                        val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                        val classVisitor = PerformanceClassVisitor(ASM9, classWriter)
                        classReader.accept(classVisitor, ClassReader.SKIP_FRAMES)
                        classWriter.toByteArray()
                    }
                    outputHandler.write(name, bytes)
                }
                tasks.add(task)
            }
        }
    }

    fun awaitComplete() {
        tasks.forEach { it.get() }
        tasks.clear()
    }

    private fun filter(file: File): Boolean {
        // TODO: 补充更多过滤
        return file.name.endsWith("PerformanceTest.class")
    }

    private fun relativePath(directory: Directory, file: File): String {
        return directory.asFile.toURI().relativize(file.toURI()).path
    }

    private fun name(relativePath: String): String {
        return relativePath.replace(File.separatorChar, '/')
    }
}