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

package com.xiaocydx.performance.plugin

import com.xiaocydx.performance.plugin.mapping.MappingMethod
import com.xiaocydx.performance.plugin.mapping.MappingReader
import com.xiaocydx.performance.plugin.mapping.MappingWriter
import groovyjarjarasm.asm.Opcodes.ASM9
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * @author xcc
 * @date 2025/4/11
 */
internal abstract class PerformanceTask : DefaultTask() {

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun taskAction() {
        val jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(
            output.get().asFile
        )))
        allJars.get().forEach { file ->
            println("handling " + file.asFile.absolutePath)
            val jarFile = JarFile(file.asFile)
            jarFile.entries().iterator().forEach { jarEntry ->
                println("Adding from jar ${jarEntry.name}")
                jarOutput.putNextEntry(JarEntry(jarEntry.name))
                jarFile.getInputStream(jarEntry).use {
                    it.copyTo(jarOutput)
                }
                jarOutput.closeEntry()
            }
            jarFile.close()
        }
        allDirectories.get().forEach { directory ->
            println("handling " + directory.asFile.absolutePath)
            directory.asFile.walk().forEach { file ->
                if (file.isFile) {
                    if (file.name.endsWith("PerformanceTest.class")) {
                        val bytes = file.inputStream().use {
                            val classReader = ClassReader(it)
                            val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                            val classVisitor = PerformanceClassVisitor(ASM9, classWriter)
                            classReader.accept(classVisitor, ClassReader.SKIP_FRAMES)
                            classWriter.toByteArray()
                        }
                        val relativePath = directory.asFile.toURI().relativize(file.toURI())
                            .getPath()
                        println("Adding from directory ${relativePath.replace(File.separatorChar, '/')}")
                        jarOutput.putNextEntry(JarEntry(relativePath.replace(File.separatorChar, '/')))
                        jarOutput.write(bytes)
                        jarOutput.closeEntry()
                    } else {
                        val relativePath = directory.asFile.toURI().relativize(file.toURI())
                            .getPath()
                        println("Adding from directory ${relativePath.replace(File.separatorChar, '/')}")
                        jarOutput.putNextEntry(JarEntry(relativePath.replace(File.separatorChar, '/')))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(jarOutput)
                        }
                        jarOutput.closeEntry()
                    }
                }
            }
        }
        jarOutput.close()
    }

    private fun read() {
        // TODO: 实现增量才需要Reader
        val reader = MappingReader(handledMethodFile())
        val previousHandled = reader.read()
        val idGenerator = reader.idGenerator(previousHandled)
        previousHandled
    }

    private fun writer() {
        val currentIgnored = (30..50).map {
            MappingMethod(id = it, accessFlag = 0, className = "class-$it", methodName = "method-$it")
        }
        val currentHandled = (1..20).map {
            MappingMethod(id = it, accessFlag = 0, className = "class-$it", methodName = "method-$it")
        }

        val writer = MappingWriter(
            ignoredMethodFile = ignoredMethodFile(),
            handledMethodFile = handledMethodFile()
        )
        writer.writeIgnored(currentIgnored)
        writer.writeHandled(currentHandled)
    }

    private fun ignoredMethodFile() = kotlin.run {
        "${project.rootDir}/outputs/ignoredMethodMapping.text"
    }

    private fun handledMethodFile() = kotlin.run {
        "${project.rootDir}/outputs/handledMethodMapping.text"
    }
}