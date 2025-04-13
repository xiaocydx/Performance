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
 * @date 2025/4/13
 */
internal class MappingReader(private val methodFile: File) {
    private val charset = MappingMethod.charset

    constructor(methodFile: String) : this(File(methodFile))

    fun read(): List<MappingMethod> {
        if (!methodFile.exists()) return emptyList()
        val outcome = methodFile.bufferedReader(charset).useLines { lines ->
            lines.map { MappingMethod.fromOutput(it) }.toList()
        }
        return outcome
    }

    fun idGenerator(list: List<MappingMethod>): IdGenerator {
        if (list.isEmpty()) return IdGenerator()
        return IdGenerator(initial = list.maxOf { it.id })
    }
}