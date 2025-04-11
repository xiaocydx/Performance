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

package com.xiaocydx.performance.runtime.history

import android.os.SystemClock
import com.xiaocydx.performance.runtime.history.Record.Companion.ID_MAX
import com.xiaocydx.performance.runtime.history.Record.Companion.ID_SLICE

/**
 * @author xcc
 * @date 2025/4/8
 */
internal object History {
    private val recorder = Recorder(capacity = 100 * 10000)

    @JvmStatic
    fun enter(id: Int) {
        if (id >= ID_MAX) return
        recorder.enter(id, currentMs())
    }

    @JvmStatic
    fun exit(id: Int) {
        if (id >= ID_MAX) return
        recorder.exit(id, currentMs())
    }

    fun createStartMark(): Long {
        enter(id = ID_SLICE)
        return recorder.mark()
    }

    fun createEndMark(): Long {
        exit(id = ID_SLICE)
        return recorder.mark()
    }

    fun snapshot(startMark: Long, endMark: Long): Snapshot {
        return recorder.snapshot(startMark, endMark)
    }

    private inline fun currentMs(): Long {
        return SystemClock.uptimeMillis()
    }
}