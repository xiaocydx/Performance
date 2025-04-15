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

package com.xiaocydx.performance.convert

import java.io.File
import java.io.File.separator

internal class Dirs {
    private val userDir = System.getProperty("user.dir")
    private val rootDir = "${userDir}${separator}performance-convert"

    val mappingDir = File("${rootDir}${separator}outputs${separator}mapping")
    val snapshotDir = File("${rootDir}${separator}snapshot")
    val snapshotJsonDir = File(snapshotDir, "json")

    init {
        mappingDir.takeIf { !it.exists() }?.mkdirs()
        snapshotDir.takeIf { !it.exists() }?.mkdirs()
        snapshotJsonDir.takeIf { !it.exists() }?.mkdirs()
    }
}

internal data class MethodData(
    val id: Int,
    val access: Int,
    val className: String,
    val methodName: String,
    val desc: String,
) {

    companion object {
        fun fromOutput(output: String): MethodData {
            val property = output.split(',')
            val id = property[0].toInt()
            val access = property[1].toInt()
            val className = property[2].replace("/", ".")
            val name = property[3]
            val desc = property[4]
            return MethodData(id, access, className, name, desc)
        }
    }
}

@JvmInline
internal value class Record(val value: Long) {

    inline val id: Int
        get() = ((value ushr SHL_BITS_ID) and MASK_ID).toInt()

    inline val timeMs: Long
        get() = value and MASK_TIME_MS

    inline val isEnter: Boolean
        get() = (value ushr SHL_BITS_ENTER) == 1L

    companion object {
        const val SHL_BITS_ENTER = 63
        const val SHL_BITS_ID = 43
        const val MASK_ID = 0xFFFFFL
        const val MASK_TIME_MS = 0x7FFFFFFFFFFL
        const val ID_MAX = 0xFFFFF
        const val ID_SLICE = ID_MAX - 1

        @Suppress("NOTHING_TO_INLINE")
        inline fun value(id: Int, timeMs: Long, isEnter: Boolean): Long {
            var value = if (isEnter) 1L shl SHL_BITS_ENTER else 0L
            value = value or (id.toLong() shl SHL_BITS_ID) // 函数数量不超过20位
            value = value or (timeMs and MASK_TIME_MS) // ms时间不超过43位
            return value
        }
    }
}