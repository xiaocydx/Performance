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

@file:Suppress("NOTHING_TO_INLINE")

package com.xiaocydx.performance.runtime

/**
 * @author xcc
 * @date 2025/4/8
 */
internal class Recorder(private val capacity: Int) {
    private val buffer = arrayOfNulls<Record>(capacity)
    private var overflow = 0
    private var nextIndex = 0
    private var latestMark = 0L

    fun enter(id: Int, timeMs: Long = System.currentTimeMillis()) {
        record(id, timeMs, isEnter = true)
    }

    fun exit(id: Int, timeMs: Long = System.currentTimeMillis()) {
        record(id, timeMs, isEnter = false)
    }

    fun mark() = latestMark

    fun snapshot(startMark: Long, endMark: Long): Snapshot {
        val start = Mark(startMark)
        val end = Mark(endMark)
        val latest = Mark(latestMark)

        val startReal = capacity * start.overflow + start.index
        val endReal = capacity * end.overflow + end.index
        val latestReal = capacity * latest.overflow + latest.index
        if (startReal + capacity <= latestReal
                || endReal + capacity <= latestReal
                || startReal + capacity <= endReal) {
            return Snapshot(emptyArray())
        }

        val outcome: Array<Record?>
        if (start.index <= end.index) {
            val size = end.index - start.index + 1
            outcome = arrayOfNulls(size)
            System.arraycopy(buffer, start.index, outcome, 0, size)
        } else {
            val firstSize = capacity - start.index
            val secondSize = end.index + 1
            outcome = arrayOfNulls(firstSize + secondSize)
            System.arraycopy(buffer, start.index, outcome, 0, firstSize)
            System.arraycopy(buffer, 0, outcome, firstSize, secondSize)
        }

        return if (outcome.isNotEmpty() && outcome[0] == null) {
            Snapshot(emptyArray())
        } else {
            @Suppress("UNCHECKED_CAST")
            Snapshot(outcome as Array<Record>)
        }
    }

    private fun record(id: Int, timeMs: Long, isEnter: Boolean) {
        if (nextIndex == capacity) {
            overflow++
            nextIndex = 0
        }
        val currIndex = nextIndex
        buffer[currIndex] = Record(id, timeMs, isEnter)
        nextIndex++
        latestMark = Mark.value(overflow, currIndex)
    }
}

@JvmInline
internal value class Mark(val value: Long) {
    inline val overflow: Int
        get() = (value ushr INT_BITS).toInt()

    inline val index: Int
        get() = (value and INT_MASK).toInt()

    companion object {
        const val INT_BITS = 32
        const val INT_MASK = 0xFFFFFFFF

        inline fun value(overflow: Int, index: Int): Long {
            return (overflow.toLong() shl INT_BITS) or (index.toLong() and INT_MASK)
        }
    }
}

internal data class Record(val id: Int, val timeMs: Long, val isEnter: Boolean)

@JvmInline
internal value class Snapshot(val value: Array<Record>) {
    fun buildTree() = Unit
}