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

package com.xiaocydx.performance.runtime.history.sample

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread

/**
 * @author xcc
 * @date 2025/4/25
 */
@MainThread
internal sealed class Sampler(
    private val sampleLooper: Looper,
    private val sampleIntervalMillis: Long
) {
    private val handler = Handler(sampleLooper)
    private val action = Runnable { sample() }
    private var isStart = false

    fun start() {
        if (isStart) return
        isStart = true
        handler.postDelayed(action, sampleIntervalMillis)
    }

    fun stop() {
        if (!isStart) return
        isStart = false
        handler.removeCallbacks(action)
    }

    private fun sample() {
        onSample()
        handler.postDelayed(action, sampleIntervalMillis)
    }

    protected abstract fun onSample()
}