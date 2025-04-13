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

import com.xiaocydx.performance.plugin.mapping.MappingMethod.Companion.INITIAL_ID
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author xcc
 * @date 2025/4/13
 */
internal data class MappingMethod(
    val id: Int,
    val accessFlag: Int,
    val className: String,
    val methodName: String
) {

    fun toOutput(): String {
        return "${id}${DELIMITER}${accessFlag}${DELIMITER}${className}${DELIMITER}$methodName"
    }

    companion object {
        private const val DELIMITER = ','
        const val INITIAL_ID = 0
        val charset = Charsets.UTF_8

        fun fromOutput(output: String): MappingMethod {
            val property = output.split(DELIMITER)
            val id = property[0].toInt()
            val accessFlag = property[1].toInt()
            val className = property[2]
            val methodName = property[3]
            return MappingMethod(id, accessFlag, className, methodName)
        }
    }
}

internal class IdGenerator(initial: Int = INITIAL_ID) {
    private val id = AtomicInteger(initial)

    fun generate() = id.incrementAndGet()
}