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

import androidx.annotation.CallSuper
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

    fun append(params: Params) {
        if (merge(params)) return
        if (size == capacity) removeFirst()
        val node = pool.acquire() ?: Node()
        node.set(params)
        addToLast(node)
    }

    private fun merge(params: Params): Boolean {
        if (params.isSingle) return false
        val last = lastOrNull()
        if (last == null || last.isSingle || last.scene != params.scene) return false
        val idleDurationMillis = params.startUptimeMillis - last.endUptimeMillis
        if (idleDurationMillis > idleThresholdMillis) return false
        val lastDurationMillis = last.endUptimeMillis - last.startUptimeMillis
        val currDurationMillis = params.endUptimeMillis - params.startUptimeMillis
        if (lastDurationMillis + currDurationMillis > mergeThresholdMillis) return false

        last.count++
        last.idleDurationMillis += idleDurationMillis
        last.endUptimeMillis = params.endUptimeMillis
        last.setLatest(params)
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

    private class Node : Params() {
        var count = 1
        var idleDurationMillis = 0L
        var prev: Node? = null
        var next: Node? = null

        override fun reset() {
            super.reset()
            count = 1
            idleDurationMillis = 0L
            prev = null
            next = null
        }
    }

    open class Params {
        var scene = Scene.Message
        var isSingle = false
        var startUptimeMillis = 0L
        var endUptimeMillis = 0L

        var startMark = 0L
        var endMark = 0L
        var sampleData: SampleData? = null

        //region Metadata
        // scene = Scene.Message
        var what = 0
        var targetName = ""
        var callbackName = ""
        var arg1 = 0
        var arg2 = 0

        //scene = IdleHandler
        var idleHandlerName = ""

        //scene = NativeTouch
        var action = 0
        var x = 0f
        var y = 0f
        //endregion

        @CallSuper
        open fun reset() {
            set(emptyParams)
        }

        fun set(params: Params) {
            scene = params.scene
            isSingle = params.isSingle
            startUptimeMillis = params.startUptimeMillis
            endUptimeMillis = params.endUptimeMillis
            setLatest(params)
        }

        fun setLatest(params: Params) {
            startMark = params.startMark
            endMark = params.endMark
            sampleData = params.sampleData
            what = params.what
            targetName = params.targetName
            callbackName = params.callbackName
            arg1 = params.arg1
            arg2 = params.arg2
            idleHandlerName = params.idleHandlerName
            action = params.action
            x = params.x
            y = params.y
        }

        fun metadata(): String {
            return TODO()
        }
    }

    private companion object {
        val emptyParams = Params()
    }
}