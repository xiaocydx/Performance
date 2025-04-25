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

import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.runtime.history.record.Node.Companion.ROOT_ID

/**
 * @author xcc
 * @date 2025/4/25
 */
@JvmInline
value class Snapshot internal constructor(private val value: LongArray) {

    val size: Int
        get() = value.size

    val isEmpty: Boolean
        get() = value.isEmpty()

    val isAvailable: Boolean
        get() {
            if (value.isEmpty()) return false
            val first = Record(value.first())
            val last = Record(value.last())
            if (!first.isEnter) return false
            if (value.size > 1 && first.timeMs > last.timeMs) return false
            return true
        }

    operator fun get(index: Int): Record {
        return Record(value[index])
    }

    @VisibleForTesting
    @Suppress("UNCHECKED_CAST")
    internal fun buildTree(candidateMs: Long = currentMs()): Node {
        val root = Node(ROOT_ID, candidateMs, candidateMs, isComplete = false, emptyList())
        if (!isAvailable) return root

        val stack = mutableListOf<Any>()
        stack.add(mutableListOf<Node>())
        value.forEach {
            val record = Record(it)
            if (record.isEnter) {
                stack.add(record)
                stack.add(mutableListOf<Node>())
            } else {
                val children = stack.removeLast() as List<Node>
                val start = stack.removeLast() as Record
                val node = Node(start.id, start.timeMs, record.timeMs, isComplete = true, children)
                val parent = stack.last() as MutableList<Node>
                parent.add(node)
            }
        }

        while (stack.size > 1) {
            val children = stack.removeLast() as List<Node>
            val start = stack.removeLast() as Record
            val node = Node(start.id, start.timeMs, candidateMs, isComplete = false, children)
            val parent = stack.last() as MutableList<Node>
            parent.add(node)
        }

        val children = stack.first() as List<Node>
        return root.copy(
            startMs = children.first().startMs,
            endMs = children.last().endMs,
            isComplete = children.last().isComplete,
            children = children
        )
    }
}

@VisibleForTesting
internal data class Node(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val isComplete: Boolean,
    val children: List<Node>,
) {
    val durationMs = endMs - startMs

    companion object {
        const val ROOT_ID = -1
    }
}