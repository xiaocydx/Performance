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
import com.xiaocydx.performance.runtime.looper.Scene

/**
 * @author xcc
 * @date 2025/4/23
 */
internal class DispatchChain(private val capacity: Int) {
    private val pool = SimplePool<Node>(50)
    private val head = Node()
    private val tail = Node()
    private var size = 0

    init {
        head.next = tail
        tail.prev = head
    }

    fun add(scene: Scene, metadata: String) {
        if (size == capacity) removeFirst()
        val node = pool.acquire() ?: Node()
        node.scene = scene
        node.metadata = metadata
        addToLast(node)
    }

    fun clear() {
        head.next = tail
        tail.prev = head
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
        val next = head.next!!
        next.prev = node
        node.next = next
        head.next = node
        node.prev = head
        size++
    }

    private class Node {
        var scene = Scene.Message
        var count = 0
        var startMark = 0L
        var endMark = 0L
        var startUptimeMillis = 0L
        var endUptimeMillis = 0L
        var metadata = ""
        var sampleState: Thread.State? = null
        var sampleStack: Array<StackTraceElement>? = null
        var prev: Node? = null
        var next: Node? = null

        fun reset() {
            scene = Scene.Message
            count = 0
            startMark = 0L
            endMark = 0L
            startUptimeMillis = 0L
            endUptimeMillis = 0L
            metadata = ""
            sampleState = null
            sampleStack = null
            prev = null
            next = null
        }
    }
}