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

import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock

/**
 * @author xcc
 * @date 2025/4/21
 */
internal class ANRWatchDog(
    private val ams: ActivityManager,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) : Thread("PerformanceANRWatchDog") {
    private val handler = Handler(Looper.getMainLooper())
    private val detection = Detection()

    override fun run() {
        while (!isInterrupted) {
            detection.post()
            try {
                sleep(intervalMillis)
            } catch (e: InterruptedException) {
                return
            }

            // FIXME: 命中不了
            if (detection.completeMillis == 0L) {
                if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) continue
                var count = 3
                var processErrorStateInfo: ProcessErrorStateInfo? = null
                val pid = Process.myPid()
                while (count > 0) {
                    count--
                    val processesInErrorState = ams.processesInErrorState
                    processErrorStateInfo = processesInErrorState?.find { it.pid == pid }
                    if (processErrorStateInfo != null) break
                    try {
                        sleep(1000)
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                processErrorStateInfo?.let {
                    println("ANRWatchDog  ${it.shortMsg}, ${it.longMsg},")
                }
            }
        }
    }

    override fun interrupt() {
        super.interrupt()
        detection.remove()
    }

    private inner class Detection : Runnable {
        @Volatile private var isPost = false
        @Volatile var completeMillis = 0L; private set

        fun post() {
            if (isPost) return
            isPost = true
            completeMillis = 0L
            handler.post(this)
        }

        fun remove() {
            handler.removeCallbacks(this)
        }

        override fun run() {
            isPost = false
            completeMillis = SystemClock.uptimeMillis()
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5000L
    }
}