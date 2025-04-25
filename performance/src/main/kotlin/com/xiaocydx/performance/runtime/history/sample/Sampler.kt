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
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread

/**
 * @author xcc
 * @date 2025/4/25
 */
internal sealed class Sampler(looper: Looper, private val intervalMillis: Long) {
    private val handler = Handler(looper)
    private val sampleTask = SampleTask()
    @Volatile private var stopUptimeMillis = 0L
    @Volatile private var sampleUptimeMillis = 0L

    protected val mainThread = Looper.getMainLooper().thread

    @MainThread
    fun start(uptimeMillis: Long) {
        stopUptimeMillis = Long.MAX_VALUE
        resetSampleUptime(uptimeMillis)
        sampleTask.startIfNecessary()
        // 代替每次调用handler.postDelayed(sampleTask, intervalMillis)
    }

    @MainThread
    fun stop(uptimeMillis: Long) {
        stopUptimeMillis = uptimeMillis
        // 代替每次调用handler.removeCallbacks(sampleTask)
    }

    @AnyThread
    private fun resetSampleUptime(uptimeMillis: Long) {
        sampleUptimeMillis = (uptimeMillis + intervalMillis).coerceAtLeast(sampleUptimeMillis)
    }

    @WorkerThread
    protected abstract fun sample()

    private inner class SampleTask : Runnable {
        @Volatile private var isStarted = false

        @MainThread
        fun startIfNecessary() {
            if (isStarted) return
            isStarted = true
            handler.postDelayed(this, intervalMillis)
        }

        @WorkerThread
        override fun run() {
            var currentTime = SystemClock.uptimeMillis()
            var stopTime = stopUptimeMillis
            if (currentTime <= stopTime) {
                isStarted = false
                return
            }

            var sampleTime = sampleUptimeMillis
            if (currentTime < sampleTime) {
                handler.postDelayed(this, sampleTime - currentTime)
                return
            }

            sample()

            currentTime = SystemClock.uptimeMillis()
            stopTime = stopUptimeMillis
            if (currentTime <= stopTime) {
                isStarted = false
                return
            }

            sampleTime = sampleUptimeMillis
            if (currentTime < sampleTime) {
                handler.postDelayed(this, sampleTime - currentTime)
            } else {
                resetSampleUptime(currentTime)
                handler.postDelayed(this, intervalMillis)
            }
        }
    }
}