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

@file:Suppress("TestFunctionName")

package com.xiaocydx.performance.runtime.history

import com.google.common.truth.Truth.assertThat
import com.xiaocydx.performance.runtime.history.Node.Companion.ROOT_ID
import org.junit.Test

/**
 * [Recorder]的单元测试
 *
 * @author xcc
 * @date 2025/4/8
 */
internal class RecorderTest {

    @Test
    fun recordValue() {
        val timeMs1 = System.currentTimeMillis()
        val value1 = Record.value(id = 1, timeMs = timeMs1, isEnter = true)
        val timeMs2 = System.currentTimeMillis()
        val value2 = Record.value(id = 1, timeMs = timeMs2, isEnter = false)

        val record1 = Record(value1)
        assertThat(record1.id).isEqualTo(1)
        assertThat(record1.timeMs).isEqualTo(timeMs1)
        assertThat(record1.isEnter).isTrue()

        val record2 = Record(value2)
        assertThat(record2.id).isEqualTo(1)
        assertThat(record2.timeMs).isEqualTo(timeMs2)
        assertThat(record2.isEnter).isFalse()
    }

    @Test
    fun markValue() {
        val value1 = Mark.value(overflow = 1, index = Int.MAX_VALUE)
        assertThat(Mark(value1).overflow).isEqualTo(1)
        assertThat(Mark(value1).index).isEqualTo(Int.MAX_VALUE)

        val value2 = Mark.value(overflow = Int.MAX_VALUE, index = 1)
        assertThat(Mark(value2).overflow).isEqualTo(Int.MAX_VALUE)
        assertThat(Mark(value2).index).isEqualTo(1)

        val value3 = Mark.value(overflow = Int.MAX_VALUE, index = Int.MAX_VALUE)
        assertThat(Mark(value3).overflow).isEqualTo(Int.MAX_VALUE)
        assertThat(Mark(value3).index).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun emptyMark() {
        val recorder = Recorder(CAPACITY)
        val start = recorder.mark()
        val end = recorder.mark()
        assertThat(Mark(start).index).isEqualTo(0)
        assertThat(Mark(start).overflow).isEqualTo(0)
        assertThat(Mark(end).index).isEqualTo(0)
        assertThat(Mark(end).overflow).isEqualTo(0)
    }

    @Test
    fun notOverflowMark() {
        val recorder = Recorder(CAPACITY)
        val start = recorder.mark()
        // A(en) B(en) B(ex) C(en) C(ex) A(ex)
        A(recorder)
        val end = recorder.mark()

        assertThat(Mark(start).index).isEqualTo(0)
        assertThat(Mark(start).overflow).isEqualTo(0)
        assertThat(Mark(end).index).isEqualTo(CAPACITY - 1)
        assertThat(Mark(end).overflow).isEqualTo(0)
    }

    @Test
    fun overflowMark() {
        val recorder = Recorder(CAPACITY)
        // B(en) B(ex) C(en) C(ex) B(en) B(ex)
        B(recorder)
        C(recorder)
        B(recorder)

        val start = recorder.mark()
        // C'(en) C'(ex) C(en) C(ex) B(en) B(ex)
        C(recorder)
        val end = recorder.mark()

        assertThat(Mark(start).index).isEqualTo(CAPACITY - 1)
        assertThat(Mark(start).overflow).isEqualTo(0)
        assertThat(Mark(end).index).isEqualTo(1)
        assertThat(Mark(end).overflow).isEqualTo(1)
    }

    @Test
    fun emptySnapshot() {
        val recorder = Recorder(CAPACITY)
        val start = recorder.mark()
        val end = recorder.mark()
        assertThat(recorder.snapshot(start, end).value).isEmpty()
    }

    @Test
    fun notOverflowSnapshot() {
        val recorder = Recorder(CAPACITY)
        val start = recorder.mark()
        // A(en) B(en) B(ex) C(en) C(ex) A(ex)
        A(recorder)
        val end = recorder.mark()

        val snapshot = recorder.snapshot(start, end)
        snapshot.assertThat(index = 0, id = ID_A, isEnter = true)
        snapshot.assertThat(index = 1, id = ID_B, isEnter = true)
        snapshot.assertThat(index = 2, id = ID_B, isEnter = false)
        snapshot.assertThat(index = 3, id = ID_C, isEnter = true)
        snapshot.assertThat(index = 4, id = ID_C, isEnter = false)
        snapshot.assertThat(index = 5, id = ID_A, isEnter = false)
    }

    @Test
    fun overflowSnapshot() {
        val recorder = Recorder(CAPACITY)
        // B(en) B(ex) C(en) C(ex) B(en) B(ex)
        B(recorder)
        C(recorder)
        B(recorder)

        val start = recorder.mark()
        // C'(en) C'(ex) C(en) C(ex) B(en) B(ex)
        C(recorder)
        val end = recorder.mark()

        // B(ex) C'(en) C'(ex)
        val snapshot = recorder.snapshot(start, end)
        snapshot.assertThat(index = 0, id = ID_B, isEnter = false)
        snapshot.assertThat(index = 1, id = ID_C, isEnter = true)
        snapshot.assertThat(index = 2, id = ID_C, isEnter = false)
    }

    @Test
    fun invalidSnapshot() {
        val recorder = Recorder(CAPACITY)
        // A(en) B(en) B(ex) C(en) C(ex) A(ex)
        A(recorder)

        val start = recorder.mark()
        // A'(en) B'(en) B'(ex) C'(en) C'(ex) A'(ex)
        A(recorder)
        val end = recorder.mark()
        assertThat(recorder.snapshot(start, end).value).isEmpty()
    }

    @Test
    fun buildTreeComplete() {
        val recorder = Recorder(8)
        val start = recorder.mark()
        // 1(en) 2(en) 3(en) 3(ex) 2(ex) 1(ex) 4(en) 4(ex)
        recorder.enter(id = 1)
        recorder.enter(id = 2)
        recorder.enter(id = 3)
        recorder.exit(id = 3)
        recorder.exit(id = 2)
        recorder.exit(id = 1)
        recorder.enter(id = 4)
        recorder.exit(id = 4)
        val end = recorder.mark()

        // node.isComplete = true，表示调用完整
        val snapshot = recorder.snapshot(start, end)
        val root = snapshot.buildTree()
        root.assertThat(id = ROOT_ID, isComplete = true, childrenSize = 2)
        val node1 = root.children.first()
        node1.assertThat(id = 1, isComplete = true, childrenSize = 1)
        val node2 = node1.children.first()
        node2.assertThat(id = 2, isComplete = true, childrenSize = 1)
        val node3 = node2.children.first()
        node3.assertThat(id = 3, isComplete = true, childrenSize = 0)
        val node4 = root.children.last()
        node4.assertThat(id = 4, isComplete = true, childrenSize = 0)
    }

    @Test
    fun buildTreeIncomplete() {
        val recorder = Recorder(8)
        val start = recorder.mark()
        // 1(en) 2(en) 3(en) 3(ex) 4(en)
        recorder.enter(id = 1)
        recorder.enter(id = 2)
        recorder.enter(id = 3)
        recorder.exit(id = 3)
        recorder.enter(id = 4)
        val end = recorder.mark()

        // node.isComplete = false，表示调用不完整
        // 1(en) 2(en) 3(en) 3(ex) 4(en) | 4(ex) 2(ex) 1(ex)
        // | 后面是用candidateMs补全的记录
        val snapshot = recorder.snapshot(start, end)
        val root = snapshot.buildTree(candidateMs = currentMs())
        root.assertThat(id = ROOT_ID, isComplete = false, childrenSize = 1)
        val node1 = root.children.first()
        node1.assertThat(id = 1, isComplete = false, childrenSize = 1)
        val node2 = node1.children.first()
        node2.assertThat(id = 2, isComplete = false, childrenSize = 2)
        val node3 = node2.children.first()
        node3.assertThat(id = 3, isComplete = true, childrenSize = 0)
        val node4 = node2.children.last()
        node4.assertThat(id = 4, isComplete = false, childrenSize = 0)
    }

    private fun A(recorder: Recorder) {
        recorder.enter(ID_A)
        B(recorder)
        C(recorder)
        recorder.exit(ID_A)
    }

    private fun B(recorder: Recorder) {
        recorder.enter(ID_B)
        recorder.exit(ID_B)
    }

    private fun C(recorder: Recorder) {
        recorder.enter(ID_C)
        recorder.exit(ID_C)
    }

    private fun Snapshot.assertThat(index: Int, id: Int, isEnter: Boolean) {
        assertThat(get(index).id).isEqualTo(id)
        assertThat(get(index).isEnter).isEqualTo(isEnter)
    }

    private fun Node.assertThat(id: Int, isComplete: Boolean, childrenSize: Int) {
        assertThat(id).isEqualTo(id)
        assertThat(isComplete).isEqualTo(isComplete)
        assertThat(children).hasSize(childrenSize)
    }

    private companion object {
        const val ID_A = 1
        const val ID_B = 2
        const val ID_C = 3
        const val CAPACITY = ID_C * 2
    }
}