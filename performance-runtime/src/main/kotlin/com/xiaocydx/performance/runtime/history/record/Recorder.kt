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

package com.xiaocydx.performance.runtime.history.record

/**
 * @author xcc
 * @date 2025/4/8
 */
internal class Recorder(private val capacity: Int) {
    private val buffer = LongArray(capacity)
    private var overflow = 0
    private var nextIndex = 0
    private var latestMark = 0L

    fun enter(id: Int, timeMs: Long = currentMs()) {
        record(id, timeMs, isEnter = true)
    }

    fun exit(id: Int, timeMs: Long = currentMs()) {
        record(id, timeMs, isEnter = false)
    }

    fun mark() = latestMark

    fun snapshot(startMark: Long, endMark: Long): Snapshot {
        val start = Mark(startMark)
        val end = Mark(endMark)
        val latest = Mark(latestMark)
        if (!Mark.checkRange(start, latest)
                || !Mark.checkRange(end, latest)
                || !Mark.checkRange(start, end)) {
            return Snapshot.empty()
        }

        val outcome: LongArray
        if (start.index <= end.index) {
            val size = end.index - start.index + 1
            outcome = LongArray(size)
            System.arraycopy(buffer, start.index, outcome, 0, size)
        } else {
            val firstSize = capacity - start.index
            val secondSize = end.index + 1
            outcome = LongArray(firstSize + secondSize)
            System.arraycopy(buffer, start.index, outcome, 0, firstSize)
            System.arraycopy(buffer, 0, outcome, firstSize, secondSize)
        }

        return if (outcome.isNotEmpty() && outcome[0] == 0L) Snapshot.empty() else Snapshot(outcome)
    }

    private fun record(id: Int, timeMs: Long, isEnter: Boolean) {
        if (nextIndex == capacity) {
            overflow++
            nextIndex = 0
        }
        val currIndex = nextIndex
        buffer[currIndex] = Record.value(id, timeMs, isEnter)
        nextIndex++
        latestMark = Mark.value(overflow, currIndex)
    }
}

internal inline fun currentMs(): Long {
    return System.currentTimeMillis()
}