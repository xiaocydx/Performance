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

package com.xiaocydx.performance.plugin.output

import org.gradle.api.file.RegularFileProperty
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.Deflater

/**
 * @author xcc
 * @date 2025/4/20
 */
internal class JarOutput(outputJar: RegularFileProperty) {
    private val jar = JarOutputStream(outputJar.get().asFile.outputStream().buffered())

    init {
        jar.setLevel(Deflater.NO_COMPRESSION)
    }

    fun write(entryName: String, bytes: ByteArray) {
        synchronized(jar) {
            jar.putNextEntry(JarEntry(entryName))
            jar.write(bytes)
            jar.closeEntry()
        }
    }

    fun write(file: JarFile, entry: JarEntry) {
        synchronized(jar) {
            jar.putNextEntry(JarEntry(entry.name))
            file.getInputStream(entry).use { it.copyTo(jar) }
            jar.closeEntry()
        }
    }

    fun write(entryName: String, file: File) {
        synchronized(jar) {
            jar.putNextEntry(JarEntry(entryName))
            file.inputStream().use { it.copyTo(jar) }
            jar.closeEntry()
        }
    }

    fun close() {
        synchronized(jar) { jar.close() }
    }
}