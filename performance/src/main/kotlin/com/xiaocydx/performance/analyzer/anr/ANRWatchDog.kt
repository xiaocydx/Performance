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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package com.xiaocydx.performance.analyzer.anr

import android.app.ActivityManager
import android.app.ActivityManager.ProcessErrorStateInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.Start

/**
 * @author xcc
 * @date 2025/4/21
 */
internal class ANRWatchDog(
    private val ams: ActivityManager,
    private val intervalMillis: Long = 5000L
) : LooperCallback {
    private val anrTask = ANRTask()
    private val detectionTask = DetectionTask()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchDogThread = HandlerThread("PerformanceANRWatchDog", THREAD_PRIORITY_BACKGROUND)
    private lateinit var watchHandler: Handler
    private var anrAction: (() -> Unit)? = null
    private var startUptimeMillis = 0L

    fun start(anrAction: () -> Unit) {
        this.anrAction = anrAction
        watchDogThread.start()
        watchHandler = Handler(watchDogThread.looper)
        detectionTask.post()
        anrTask.post(intervalMillis)
    }

    fun stop() {
        watchDogThread.interrupt()
        watchDogThread.quit()
    }

    override fun dispatch(current: DispatchContext) {
        when (current) {
            is Start -> {
                startUptimeMillis = current.uptimeMillis
            }
            is End -> {
                if (current.uptimeMillis - startUptimeMillis >= intervalMillis) {
                    detectionTask.remove()
                    anrTask.direct()
                }
            }
        }
    }

    private inner class DetectionTask : Runnable {
        @Volatile var isPost = false
        @Volatile var isComplete = false; private set

        @AnyThread
        fun post() {
            if (isPost) return
            isPost = true
            isComplete = false
            mainHandler.post(this)
        }

        @AnyThread
        fun remove() {
            if (!isPost) return
            isPost = false
            isComplete = false
            mainHandler.removeCallbacks(this)
        }

        @MainThread
        override fun run() {
            isPost = false
            isComplete = true
        }
    }

    private inner class ANRTask : Runnable {
        private var pid = 0
        @Volatile var isPost = false
        @Volatile var isRunning = false; private set

        @AnyThread
        fun post(delayMillis: Long) {
            if (isPost) return
            isPost = true
            watchHandler.postDelayed(this, delayMillis)
        }

        @AnyThread
        fun remove() {
            if (!isPost) return
            isPost = false
            watchHandler.removeCallbacks(this)
        }

        @AnyThread
        fun direct() {
            if (anrTask.isRunning) return
            remove()
            post(delayMillis = 0)
        }

        @WorkerThread
        override fun run() {
            isPost = false
            isRunning = true
            if (!detectionTask.isComplete) {
                if (pid == 0) pid = Process.myPid()
                var retryCount = 6
                var processErrorStateInfo: ProcessErrorStateInfo? = null
                while (retryCount > 0) {
                    retryCount--
                    val processesInErrorState = ams.processesInErrorState
                    processErrorStateInfo = processesInErrorState?.find { it.pid == pid }
                    if (processErrorStateInfo != null) break
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                val anrAction = anrAction
                if (processErrorStateInfo != null) anrAction?.invoke()
            }
            isRunning = false
            detectionTask.post()
            post(intervalMillis)
        }
    }
}