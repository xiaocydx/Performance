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

import org.gradle.api.file.DirectoryProperty
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.CRC32

/**
 * @author xcc
 * @date 2025/4/20
 */
internal class CacheOutput(cacheDirectory: DirectoryProperty) {
    val cacheDir = requireNotNull(cacheDirectory.get().asFile)

    init {
        cacheDir.takeIf { !it.exists() }?.mkdirs()
    }

    fun cacheFile(entry: JarEntry): File {
        val crc = entry.crc.toString(16)
        return File(cacheDir, "${entry.name}$CACHE_SEPARATOR${crc}")
    }

    fun cacheFile(entryName: String, file: File): File {
        val crc = CRC32()
        file.inputStream().use { stream ->
            var bytesRead: Int
            val buffer = ByteArray(8192)
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        return File(cacheDir, "${entryName}${CACHE_SEPARATOR}${crc.value}")
    }

    fun write(bytes: ByteArray, cache: File) {
        if (!cache.exists()) {
            cache.ensureParentFileExists()
            cache.outputStream().use { it.write(bytes) }
        }
    }

    fun write(file: JarFile, entry: JarEntry, cache: File) {
        if (!cache.exists()) {
            cache.ensureParentFileExists()
            cache.outputStream().useCopyFrom(file.getInputStream(entry))
        }
    }

    fun write(file: File, cache: File) {
        if (!cache.exists()) {
            cache.ensureParentFileExists()
            cache.outputStream().useCopyFrom(file.inputStream())
        }
    }

    private fun File.ensureParentFileExists() {
        parentFile?.takeIf { !it.exists() }?.mkdirs()
    }

    private fun OutputStream.useCopyFrom(inputStream: InputStream) {
        inputStream.use { it.copyTo(this) }
    }

    companion object {
        const val CACHE_SEPARATOR = "_cache_"
    }
}