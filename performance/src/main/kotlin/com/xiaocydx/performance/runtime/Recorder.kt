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

import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.runtime.Node.Companion.ROOT_ID

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

        val startReal = capacity * start.overflow + start.index
        val endReal = capacity * end.overflow + end.index
        val latestReal = capacity * latest.overflow + latest.index
        if (startReal + capacity <= latestReal
                || endReal + capacity <= latestReal
                || startReal + capacity <= endReal) {
            return Snapshot(longArrayOf())
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

        return if (outcome.isNotEmpty() && outcome[0] == 0L) {
            Snapshot(longArrayOf())
        } else {
            Snapshot(outcome)
        }
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

@JvmInline
internal value class Record(val value: Long) {

    inline val id: Int
        get() = ((value ushr ID_SHL_BITS) and ID_MASK).toInt()

    inline val timeMs: Long
        get() = value and TIME_MS_MASK

    inline val isEnter: Boolean
        get() = (value ushr ENTER_SHL_BITS) == 1L

    companion object {
        const val ENTER_SHL_BITS = 63
        const val ID_SHL_BITS = 43
        const val ID_MASK = 0xFFFFFL
        const val TIME_MS_MASK = 0x7FFFFFFFFFFL

        inline fun value(id: Int, timeMs: Long, isEnter: Boolean): Long {
            var value = if (isEnter) 1L shl ENTER_SHL_BITS else 0L
            value = value or (id.toLong() shl ID_SHL_BITS) // 函数数量不超过20位
            value = value or (timeMs and TIME_MS_MASK) // ms时间不超过43位
            return value
        }
    }
}

internal inline fun currentMs(): Long {
    return System.currentTimeMillis()
}

@JvmInline
internal value class Mark(val value: Long) {
    inline val overflow: Int
        get() = (value ushr INT_BITS).toInt()

    inline val index: Int
        get() = (value and INT_MASK).toInt()

    companion object {
        const val INT_BITS = 32
        const val INT_MASK = 0xFFFFFFFFL

        inline fun value(overflow: Int, index: Int): Long {
            return (overflow.toLong() shl INT_BITS) or (index.toLong() and INT_MASK)
        }
    }
}

@JvmInline
internal value class Snapshot(val value: LongArray) {

    inline fun get(index: Int): Record {
        return Record(value[index])
    }

    @VisibleForTesting
    @Suppress("UNCHECKED_CAST")
    fun buildTree(candidateMs: Long = currentMs()): Node {
        val root = Node(ROOT_ID, candidateMs, candidateMs, isComplete = false, emptyList())
        if (value.isEmpty()) return root

        val first = Record(value.first())
        val last = Record(value.last())
        if (!first.isEnter) return root
        if (value.size > 1 && first.timeMs > last.timeMs) return root

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