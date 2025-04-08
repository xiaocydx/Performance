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

package com.xiaocydx.performance.runtime

/**
 * // TODO: 2025/4/8 抽离出Record逻辑
 *
 * @author xcc
 * @date 2025/4/8
 */
internal class Recorder(private val capacity: Int) {
    private val buffer = arrayOfNulls<Record>(capacity)
    private var nextIndex = 0
    private var overflow = 0

    fun enter(id: Int, timeMs: Long = System.currentTimeMillis()) {
        record(id, timeMs, isEnter = true)
    }

    fun exit(id: Int, timeMs: Long = System.currentTimeMillis()) {
        record(id, timeMs, isEnter = false)
    }

    fun mark(): Mark {
        return Mark(nextIndex - 1)
    }

    @Suppress("UNCHECKED_CAST", "UnnecessaryVariable")
    fun snapshot(mark: Mark): Array<Record> {
        val start = mark.index.coerceAtLeast(0)
        val end = nextIndex.coerceAtLeast(0)
        return when {
            overflow == 0 && end == 0 -> arrayOf()
            overflow > 0 && (start + capacity < end + capacity * overflow) -> arrayOf()
            start < end -> {
                val size = end - start
                val outcome = arrayOfNulls<Record>(size)
                System.arraycopy(buffer, start, outcome, 0, size)
                outcome as Array<Record>
            }
            else -> {
                val firstSize = capacity - start
                val secondSize = end
                val outcome = arrayOfNulls<Record>(firstSize + secondSize)
                System.arraycopy(buffer, start, outcome, 0, firstSize)
                System.arraycopy(buffer, 0, outcome, firstSize, secondSize)
                outcome as Array<Record>
            }
        }
    }

    private fun record(id: Int, timeMs: Long, isEnter: Boolean) {
        if (nextIndex == capacity) {
            nextIndex = 0
            overflow++
        }
        buffer[nextIndex] = Record(id, timeMs, isEnter)
        nextIndex++
    }

    // TODO: 有更多信息？
    data class Mark(val index: Int)

    // TODO: 优化为一个Long
    data class Record(val id: Int, val timeMs: Long, val isEnter: Boolean)
}