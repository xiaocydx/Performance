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
import com.xiaocydx.performance.runtime.Recorder.Record
import org.junit.Test

/**
 * [Recorder]的单元测试
 *
 * @author xcc
 * @date 2025/4/8
 */
internal class RecorderTest {

    @Test
    fun empty() {
        val recorder = Recorder(CAPACITY)
        val mark = recorder.mark()
        val snapshot = recorder.snapshot(mark)
        assertThat(snapshot).isEmpty()
    }

    @Test
    fun notOverflow() {
        val recorder = Recorder(CAPACITY)
        val mark = recorder.mark()
        // A(en) B(en) B(ex) C(en) C(ex) A(ex)
        A(recorder)

        val snapshot = recorder.snapshot(mark)
        snapshot.assertThat(index = 0, id = ID_A, isEnter = true)
        snapshot.assertThat(index = 1, id = ID_B, isEnter = true)
        snapshot.assertThat(index = 2, id = ID_B, isEnter = false)
        snapshot.assertThat(index = 3, id = ID_C, isEnter = true)
        snapshot.assertThat(index = 4, id = ID_C, isEnter = false)
        snapshot.assertThat(index = 5, id = ID_A, isEnter = false)
    }

    @Test
    fun overflow() {
        val recorder = Recorder(CAPACITY)
        // B(en) B(ex) C(en) C(ex) B(en) B(ex)
        B(recorder)
        C(recorder)
        B(recorder)

        val mark = recorder.mark()
        // C'(en) C'(ex) C(en) C(ex) B(en) B(ex)
        C(recorder)
        // B(ex) C'(en) C'(ex)
        val snapshot = recorder.snapshot(mark)
        snapshot.assertThat(index = 0, id = ID_B, isEnter = false)
        snapshot.assertThat(index = 1, id = ID_C, isEnter = true)
        snapshot.assertThat(index = 2, id = ID_C, isEnter = false)
    }

    @Test
    fun invalidMark() {
        val recorder = Recorder(CAPACITY)
        // A(en) B(en) B(ex) C(en) C(ex) A(ex)
        A(recorder)

        val mark = recorder.mark()
        assertThat(recorder.snapshot(mark)).hasLength(1)

        // A'(en) B'(en) B'(ex) C'(en) C'(ex) A'(ex)
        A(recorder)
        assertThat(recorder.snapshot(mark)).isEmpty()
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

    private fun Array<Record>.assertThat(index: Int, id: Int, isEnter: Boolean) {
        assertThat(get(index).id).isEqualTo(id)
        assertThat(get(index).isEnter).isEqualTo(isEnter)
    }

    private companion object {
        const val ID_A = 1
        const val ID_B = 2
        const val ID_C = 3
        const val CAPACITY = ID_C * 2
    }
}