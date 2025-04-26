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
        if (!merge(segment)) append(segment)
        segment.reset()
    }

    private fun append(segment: Segment) {
        val element = if (deque.size == capacity) deque.removeFirst() else Element()
        element.init(segment)
        deque.add(element)
    }

    private fun merge(segment: Segment): Boolean {
        if (segment.isSingle) return false
        val element = deque.lastOrNull()
        if (element == null || element.isSingle || element.scene != segment.scene) return false

        val idleDuration = segment.startUptimeMillis - element.endUptimeMillis
        if (idleDuration > idleThresholdMillis) return false

        val lastDuration = element.endUptimeMillis - element.startUptimeMillis
        val currDuration = segment.endUptimeMillis - segment.startUptimeMillis
        if (lastDuration + currDuration > mergeThresholdMillis) return false

        element.merge(segment, idleDuration)
        return true
    }

    fun peek(): List<Element> {
        return deque.toList()
    }

    fun peek(startUptimeMillis: Long, endUptimeMillis: Long): List<Element> {
        val outcome = mutableListOf<Element>()
        for (i in deque.lastIndex downTo 0) {
            val element = deque[i]
            if (element.startUptimeMillis > endUptimeMillis) continue
            if (element.endUptimeMillis < startUptimeMillis) break
            outcome.add(element)
        }
        outcome.reverse()
        return outcome
    }

    class Element {
        val last = Segment()
        var count = 1; private set
        var startMark = 0L; private set
        var startUptimeMillis = 0L; private set
        var startThreadTimeMillis = 0L; private set
        var idleDurationMillis = 0L; private set

        val isSingle get() = last.isSingle
        val scene get() = last.scene
        val endUptimeMillis get() = last.endUptimeMillis

        fun init(segment: Segment) {
            count = 1
            startMark = segment.startMark
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
    }
}