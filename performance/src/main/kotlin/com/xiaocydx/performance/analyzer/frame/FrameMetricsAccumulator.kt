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

import android.os.SystemClock
import android.view.FrameMetrics
import androidx.annotation.WorkerThread

/**
 * @author xcc
 * @date 2025/4/4
 */
@WorkerThread
internal class FrameMetricsAccumulator(
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
        if (startMillis != 0L) return
        if (receiver.skipFirstFrame && frameMetrics.isFirstDrawFrame) return
        startMillis = SystemClock.uptimeMillis()
        targetName = activityName
    }

    fun accumulate(refreshRate: Float, frameMetrics: FrameMetrics) {
        if (startMillis == 0L) return
        val frameIntervalNanos = TIME_SECOND_TO_NANOS / refreshRate
        val dropped = (frameMetrics.totalNanos - frameIntervalNanos) / frameIntervalNanos
        totalFrames++
        droppedFrames += dropped.coerceAtLeast(0f)
        refreshRates += refreshRate
    }

    fun makeEnd() {
        if (startMillis == 0L) return
        val interval = SystemClock.uptimeMillis() - startMillis
        if (interval < intervalMillis) return
        // TODO: 过滤两帧间隔较长的情况
        avgFps = totalFrames / (interval.toFloat() / 1000)
        avgDroppedFrames = droppedFrames / totalFrames
        avgRefreshRate = refreshRates / totalFrames
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

    private companion object {
        const val TIME_SECOND_TO_NANOS = 1000000000
    }
}