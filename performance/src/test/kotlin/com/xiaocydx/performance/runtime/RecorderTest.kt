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

package com.xiaocydx.performance.runtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [Recorder]的单元测试
 *
 * @author xcc
 * @date 2025/4/8
 */
internal class RecorderTest {

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
        assertThat(value[index].id).isEqualTo(id)
        assertThat(value[index].isEnter).isEqualTo(isEnter)
    }

    private companion object {
        const val ID_A = 1
        const val ID_B = 2
        const val ID_C = 3
        const val CAPACITY = ID_C * 2
    }
}