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

package com.xiaocydx.performance.runtime.history.record

/**
 * @author xcc
 * @date 2025/4/25
 */
@JvmInline
internal value class Mark(private val value: Long) {
    val overflow: Int
        get() = (value ushr INT_BITS).toInt()

    val index: Int
        get() = (value and INT_MASK).toInt()

    companion object {
        const val INT_BITS = 32
        const val INT_MASK = 0xFFFFFFFFL

        @Suppress("NOTHING_TO_INLINE")
        inline fun value(overflow: Int, index: Int): Long {
            return (overflow.toLong() shl INT_BITS) or (index.toLong() and INT_MASK)
        }

        fun checkRange(start: Mark, end: Mark): Boolean {
            return when (start.overflow) {
                end.overflow -> start.index <= end.index
                end.overflow - 1 -> end.index < start.index
                else -> false
            }
        }
    }
}