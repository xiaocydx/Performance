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

package com.xiaocydx.performance.analyzer.anr

import androidx.core.util.Pools.SimplePool
import com.xiaocydx.performance.runtime.SampleData
import com.xiaocydx.performance.runtime.looper.Scene

/**
 * @author xcc
 * @date 2025/4/23
 */
internal class ANRMetricsChain(
    private val capacity: Int,
    private val idleThresholdMillis: Long,
    private val mergeThresholdMillis: Long,
) {
    private val pool = SimplePool<Node>(50)
    private val head = Node()
    private val tail = Node()
    private var size = 0

    init {
        head.next = tail
        tail.prev = head
    }

    fun append(
        isSingle: Boolean,
        scene: Scene,
        metadata: String,
        startMark: Long,
        endMark: Long,
        startUptimeMillis: Long,
        endUptimeMillis: Long,
        sampleData: SampleData?
    ) {
        if (!isSingle && merge(scene, metadata, startMark, endMark,
                    startUptimeMillis, endUptimeMillis, sampleData)) {
            return
        }
        if (size == capacity) removeFirst()
        val node = pool.acquire() ?: Node()
        node.count++
        node.isSingle = isSingle
        node.scene = scene
        node.startMark = startMark
        node.endMark = endMark
        node.startUptimeMillis = startUptimeMillis
        node.endUptimeMillis = endUptimeMillis
        node.metadata = metadata
        addToLast(node)
    }

    private fun merge(
        scene: Scene,
        metadata: String,
        startMark: Long,
        endMark: Long,
        startUptimeMillis: Long,
        endUptimeMillis: Long,
        sampleData: SampleData?
    ): Boolean {
        val last = lastOrNull()
        if (last == null || last.isSingle || last.scene != scene) return false
        val idleDurationMillis = startUptimeMillis - last.endUptimeMillis
        if (idleDurationMillis > idleThresholdMillis) return false
        val lastDurationMillis = last.endUptimeMillis - last.startUptimeMillis
        val currDurationMillis = endUptimeMillis - startUptimeMillis
        if (lastDurationMillis + currDurationMillis > mergeThresholdMillis) return false

        last.count++
        last.startMark = startMark
        last.endMark = endMark
        last.endUptimeMillis = endUptimeMillis
        last.metadata = metadata
        last.sampleData = sampleData
        return true
    }

    fun clear() {
        head.next = tail
        tail.prev = head
    }

    private fun lastOrNull(): Node? {
        val prev = tail.prev
        if (prev == head) return null
        return prev
    }

    private fun removeFirst() {
        if (head.next == tail) return
        val node = head.next!!
        val next = node.next!!
        head.next = next
        next.prev = head
        size--
        node.reset()
        pool.release(node)
    }

    private fun addToLast(node: Node) {
        val prev = tail.prev!!
        node.next = tail
        tail.prev = node
        prev.next = node
        node.prev = prev
        size++
    }

    private fun toList(): List<Node> {
        val outcome = mutableListOf<Node>()
        var curr = head.next!!
        while (curr != tail) {
            outcome.add(curr)
            curr = curr.next!!
        }
        return outcome
    }

    private class Node {
        var count = 0
        var isSingle = false
        var scene = Scene.Message
        var startMark = 0L
        var endMark = 0L
        var startUptimeMillis = 0L
        var endUptimeMillis = 0L
        var metadata = ""
        var sampleData: SampleData? = null
        var prev: Node? = null
        var next: Node? = null

        fun reset() {
            count = 0
            isSingle = false
            scene = Scene.Message
            startMark = 0L
            endMark = 0L
            startUptimeMillis = 0L
            endUptimeMillis = 0L
            metadata = ""
            sampleData = null
            prev = null
            next = null
        }
    }
}