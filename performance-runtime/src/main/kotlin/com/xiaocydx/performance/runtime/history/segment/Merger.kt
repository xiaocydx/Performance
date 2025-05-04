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

package com.xiaocydx.performance.runtime.history.segment

import androidx.annotation.VisibleForTesting

/**
 * @author xcc
 * @date 2025/4/23
 */
internal class Merger(
    private val capacity: Int,
    private val idleThresholdMillis: Long,
    private val mergeThresholdMillis: Long,
) {
    private val deque = ArrayDeque<Range>(capacity)

    fun consume(segment: Segment) {
        if (!merge(segment)) append(segment)
        segment.reset()
    }

    @VisibleForTesting
    fun append(segment: Segment) {
        val range = if (deque.size == capacity) deque.removeFirst() else Range()
        range.init(segment)
        deque.add(range)
    }

    @VisibleForTesting
    fun merge(segment: Segment): Boolean {
        if (segment.isSingle) return false
        val range = lastOrNull()
        if (range == null || range.isSingle || range.scene != segment.scene) return false

        val idleDuration = segment.startUptimeMillis - range.endUptimeMillis
        if (idleDuration > idleThresholdMillis) return false

        val lastDuration = range.endUptimeMillis - range.startUptimeMillis
        val currDuration = segment.endUptimeMillis - segment.startUptimeMillis
        if (lastDuration + currDuration > mergeThresholdMillis) return false

        range.merge(segment, idleDuration)
        return true
    }

    @VisibleForTesting
    fun lastOrNull(): Range? {
        return deque.lastOrNull()
    }

    fun copy(): List<Range> {
        return deque.map { it.deepCopy() }
    }

    fun copy(startUptimeMillis: Long, endUptimeMillis: Long): List<Range> {
        if (startUptimeMillis < 0 || endUptimeMillis < 0
                || endUptimeMillis < startUptimeMillis) {
            return emptyList()
        }
        val outcome = mutableListOf<Range>()
        for (i in 0 until deque.size) {
            val range = deque[i]
            if (range.endUptimeMillis < startUptimeMillis) continue
            if (range.startUptimeMillis > endUptimeMillis) break
            outcome.add(range.deepCopy())
        }
        return outcome
    }

    data class Range(
        var count: Int = 1,
        var startUptimeMillis: Long = 0L,
        var startThreadTimeMillis: Long = 0L,
        var idleDurationMillis: Long = 0L,
        val last: Segment = Segment()
    ) {
        val isSingle get() = last.isSingle
        val scene get() = last.scene
        val endUptimeMillis get() = last.endUptimeMillis
        val endThreadTimeMillis get() = last.endThreadTimeMillis
        val needRecord get() = last.needRecord
        val needSample get() = last.needSample

        fun init(segment: Segment) {
            count = 1
            startUptimeMillis = segment.startUptimeMillis
            startThreadTimeMillis = segment.startThreadTimeMillis
            idleDurationMillis = 0L
            last.copyFrom(segment)
        }

        fun merge(segment: Segment, idleDuration: Long) {
            count++
            idleDurationMillis += idleDuration
            last.copyFrom(segment)
        }

        fun lastMetadata(): String {
            return last.metadata()
        }

        fun deepCopy() = copy(last = last.copy())
    }
}