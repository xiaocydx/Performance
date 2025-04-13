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

package com.xiaocydx.performance.plugin.mapping

import java.io.File

/**
 * @author xcc
 * @date 2025/4/12
 */
internal class MappingWriter(
    private val ignoredMethodFile: File,
    private val handledMethodFile: File,
) {
    private val charset = MappingMethod.charset

    constructor(
        handledMethodFile: String,
        ignoredMethodFile: String
    ) : this(File(handledMethodFile), File(ignoredMethodFile))

    fun writeIgnored(ignored: List<MappingMethod>) {
        write(ignoredMethodFile, ignored)
    }

    fun writeHandled(handled: List<MappingMethod>) {
        write(handledMethodFile, handled)
    }

    private fun write(file: File, list: List<MappingMethod>) {
        val sorted = list.sortedBy { it.id }
        file.parentFile.takeIf { !it.exists() }?.mkdirs()
        file.printWriter(charset).use { writer ->
            sorted.forEach { writer.println(it.toOutput()) }
        }
    }
}