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

package com.xiaocydx.performance.analyzer.frame

import android.app.Activity
import android.os.Handler
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.view.doOnAttach
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.ref.WeakReference

/**
 * @author xcc
 * @date 2025/4/3
 */
@RequiresApi(24)
internal class FrameAnalyzer24(private val host: Performance.Host) {
    private val scope = host.createMainScope()
    private val frameMetricsListeners = HashMap<Int, FrameMetricsListener>()
    @Volatile private var latestRefreshRate = DEFAULT_REFRESH_RATE

    fun init() {
        val handler = Handler(host.defaultLooper)
        val job = host.activityEvent.onEach {
            val activity = host.getActivity(it.activityKey)
            when (it) {
                is ActivityEvent.Created -> if (activity != null) {
                    syncRefreshRateOnAttach(activity.window)
                    val listener = FrameMetricsListener(activity)
                    frameMetricsListeners[it.activityKey] = listener
                    activity.window.addOnFrameMetricsAvailableListener(listener, handler)
                }
                is ActivityEvent.Destroyed -> {
                    frameMetricsListeners.remove(it.activityKey)?.removeListener()
                }
                else -> return@onEach
            }
        }.launchIn(scope)

        job.invokeOnCompletion {
            frameMetricsListeners.forEach { it.value.removeListener() }
            frameMetricsListeners.clear()
        }
    }

    fun cancel() {
        scope.coroutineContext.cancelChildren()
    }

    private fun syncRefreshRateOnAttach(window: Window) {
        window.decorView.doOnAttach {
            var refreshRate = DEFAULT_REFRESH_RATE
            window.decorView.display?.let {
                val displayRefreshRate = it.refreshRate
                if (displayRefreshRate >= 30.0f) refreshRate = displayRefreshRate
            }
            latestRefreshRate = refreshRate
        }
    }

    private inner class FrameMetricsListener(activity: Activity?) : Window.OnFrameMetricsAvailableListener {
        private val activityRef = activity?.let(::WeakReference)
        private val activityName = activity?.javaClass?.simpleName ?: ""
        private val frameInfo = FrameInfo()


        @MainThread
        fun removeListener() {
            activityRef?.get()?.window?.removeOnFrameMetricsAvailableListener(this)
        }

        @WorkerThread
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int
        ) {
            // TODO: 要计算平均刷新率吗？
            val refreshRate = latestRefreshRate
            frameInfo.makeAccumulateStart()
            frameInfo.accumulate(frameMetrics, refreshRate)
            frameInfo.makeAccumulateEnd()

            val copyFrameMetrics = FrameMetrics(frameMetrics)
            println("test -> copyFrameMetrics = ${copyFrameMetrics}, name = $activityName")
        }
    }

    private class FrameInfo {
        private var accStartMillis = 0L
        private var accFrames = 0
        private var accDroppedFrames = 0f
        private var accRefreshRate = 0f

        fun makeAccumulateStart() {
            if (accStartMillis != 0L) return
            accStartMillis = SystemClock.uptimeMillis()
        }

        fun accumulate(frameMetrics: FrameMetrics, refreshRate: Float) {
            if (accStartMillis == 0L) return
            val frameIntervalNanos = TIME_SECOND_TO_NANO / refreshRate
            var droppedFrames = (frameMetrics.totalNanos - frameIntervalNanos) / frameIntervalNanos
            droppedFrames = droppedFrames.coerceAtLeast(0f)

            accFrames++
            accDroppedFrames += droppedFrames
            accRefreshRate += refreshRate
        }

        fun makeAccumulateEnd() {
            if (accStartMillis == 0L || accFrames == 0) return
            val threshold = 5 * 1000
            if (SystemClock.uptimeMillis() - accStartMillis < threshold) return

            val avgFps = accFrames.toFloat() / (threshold / 1000)
            val avgDroppedFrames = accDroppedFrames / accFrames
            val avgRefreshRate = accRefreshRate / accFrames
            println("test -> avgFps = $avgFps, avgDroppedFrames = $avgDroppedFrames, avgRefreshRate = $avgRefreshRate")
            // TODO: 补充分发

            accStartMillis = 0L
            accFrames = 0
            accDroppedFrames = 0f
            accRefreshRate = 0f
        }
    }

    private companion object {
        const val DEFAULT_REFRESH_RATE = 60.0f
        const val TIME_SECOND_TO_NANO = 1000000000
    }
}