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
import com.xiaocydx.performance.log
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/3
 */
@RequiresApi(24)
internal class FrameAnalyzer24(
    config: FrameConfig,
    private val host: Performance.Host
) {
    private val scope = host.createMainScope()
    private val frameMetricsListeners = HashMap<Int, FrameMetricsListener>()
    private val frameMetricsAccumulators = config.receivers.map(::FrameMetricsAccumulator)
    @Volatile private var latestRefreshRate = DEFAULT_REFRESH_RATE

    fun init() {
        val job = scope.launch {
            val handler = Handler(host.defaultLooper)
            host.activityEvent.collect {
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
                    else -> return@collect
                }
            }
        }

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

    private inner class FrameMetricsListener(
        activity: Activity?
    ) : Window.OnFrameMetricsAvailableListener {
        private val activityRef = activity?.let(::WeakReference)
        private val activityName = activity?.javaClass?.simpleName ?: ""

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
            val timeMillis = measureTimeMillis {
                dispatchAccumulators { it.makeStart(activityName, frameMetrics) }
                dispatchAccumulators { it.accumulate(refreshRate, frameMetrics) }
                dispatchAccumulators { it.makeEnd() }
            }
            log { "FrameMetricsListener dispatchAccumulators timeMillis = $timeMillis" }
        }

        @WorkerThread
        private inline fun dispatchAccumulators(action: (FrameMetricsAccumulator) -> Unit) {
            for (i in frameMetricsAccumulators.indices) action(frameMetricsAccumulators[i])
        }
    }

    @WorkerThread
    private class FrameMetricsAccumulator(
        private val receiver: FrameMetricsReceiver
    ) : FrameMetricsAccumulation {
        private var startMillis = 0L
        private var refreshRates = 0f
        override val intervalMillis = receiver.intervalMillis
        override var targetName = ""; private set
        override var totalFrames = 0; private set
        override var droppedFrames = 0f; private set
        override var avgFps = 0f; private set
        override var avgDroppedFrames = 0f; private set
        override var avgRefreshRate = 0f; private set

        fun makeStart(activityName: String, frameMetrics: FrameMetrics) {
            // TODO: 补充是否跳过首帧的判断
            if (startMillis != 0L || frameMetrics.isFirstDrawFrame) return
            startMillis = SystemClock.uptimeMillis()
            targetName = activityName
        }

        fun accumulate(refreshRate: Float, frameMetrics: FrameMetrics) {
            if (startMillis == 0L) return
            val frameIntervalNanos = TIME_SECOND_TO_NANO / refreshRate
            val dropped = (frameMetrics.totalNanos - frameIntervalNanos) / frameIntervalNanos
            totalFrames++
            droppedFrames += dropped.coerceAtLeast(0f)
            refreshRates += refreshRate
        }

        fun makeEnd() {
            if (startMillis == 0L || totalFrames == 0) return
            if (SystemClock.uptimeMillis() - startMillis < intervalMillis) return
            // TODO: 补充至少帧数，或者周期上限
            avgFps = totalFrames / (intervalMillis.toFloat() / 1000)
            avgDroppedFrames = droppedFrames / totalFrames
            avgRefreshRate = refreshRates / totalFrames
            // TODO: 对计算值设定上限
            avgFps = avgFps.coerceAtMost(avgRefreshRate)
            receiver.onAvailable(accumulation = this)
            reset()
        }

        private fun reset() {
            startMillis = 0L
            refreshRates = 0f
            targetName = ""
            totalFrames = 0
            droppedFrames = 0f
            avgFps = 0f
            avgDroppedFrames = 0f
            avgRefreshRate = 0f
        }
    }

    private companion object {
        const val DEFAULT_REFRESH_RATE = 60.0f
        const val TIME_SECOND_TO_NANO = 1000000000
    }
}