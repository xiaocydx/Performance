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

import com.xiaocydx.performance.plugin.dispatcher.SerialDispatcher
import com.xiaocydx.performance.plugin.metadata.Inspector
import org.gradle.api.file.RegularFileProperty
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class OutputProcessor(
    private val dispatcher: SerialDispatcher,
    private val output: RegularFileProperty,
    private val inspector: Inspector,
) : AbstractProcessor() {
    private val jarOutput = JarOutputStream(output.get().asFile.outputStream().buffered())
    private val tasks = TaskCountDownLatch()

    fun write(name: String, bytes: ByteArray) {
        dispatcher.execute(tasks) {
            jarOutput.putNextEntry(JarEntry(name))
            jarOutput.write(bytes)
            jarOutput.closeEntry()
        }
    }

    fun write(name: String, file: File) {
        if (!inspector.isWritable(file)) return
        dispatcher.execute(tasks) {
            jarOutput.putNextEntry(JarEntry(name))
            file.inputStream().use { it.copyTo(jarOutput) }
            jarOutput.closeEntry()
        }
    }

    fun write(file: JarFile, entry: JarEntry) {
        if (!inspector.isWritable(entry)) return
        dispatcher.execute(tasks) {
            jarOutput.putNextEntry(JarEntry(entry.name))
            file.getInputStream(entry).use { it.copyTo(jarOutput) }
            jarOutput.closeEntry()
        }
    }

    fun close(file: JarFile) {
        dispatcher.execute(tasks) { file.close() }
    }

    fun await() {
        tasks.await()
        jarOutput.close()
    }
}