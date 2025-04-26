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
    private val deque = ArrayDeque<Element>(capacity)

    fun consume(segment: Segment) {
        if (!merge(segment)) add(segment)
        segment.reset()
    }

    private fun add(segment: Segment) {
        val element = if (deque.size == capacity) deque.removeFirst() else Element()
        element.init(segment)
        deque.add(element)
    }

    private fun merge(segment: Segment): Boolean {
        if (segment.isSingle) return false
        val last = deque.lastOrNull()
        if (last == null || last.isSingle || last.scene != segment.scene) return false

        val idleDuration = segment.startUptimeMillis - last.endUptimeMillis
        if (idleDuration > idleThresholdMillis) return false

        val lastDuration = last.endUptimeMillis - last.startUptimeMillis
        val currDuration = segment.endUptimeMillis - segment.startUptimeMillis
        if (lastDuration + currDuration > mergeThresholdMillis) return false

        last.merge(segment, idleDuration)
        return true
    }

    @VisibleForTesting
    fun toList(): List<Element> {
        return deque.toList()
    }

    @VisibleForTesting
    class Element {
        private val end = Segment()
        var count = 1; private set
        var startMark = 0L; private set
        var startUptimeMillis = 0L; private set
        var startThreadTimeMillis = 0L; private set
        var idleDurationMillis = 0L; private set

        // TODO: 补充移除end，还原prev的处理
        val isSingle get() = end.isSingle
        val scene get() = end.scene
        val endUptimeMillis get() = end.endUptimeMillis

        fun init(segment: Segment) {
            count = 1
            startMark = segment.startMark
            startUptimeMillis = segment.startUptimeMillis
            startThreadTimeMillis = segment.startThreadTimeMillis
            idleDurationMillis = 0L
            end.copyFrom(segment)
        }

        fun merge(segment: Segment, idleDuration: Long) {
            count++
            idleDurationMillis += idleDuration
            end.copyFrom(segment)
        }
    }
}