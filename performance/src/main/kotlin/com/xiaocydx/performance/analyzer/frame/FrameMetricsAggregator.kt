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
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregate.Companion.NANOS_PER_SECOND
import com.xiaocydx.performance.analyzer.frame.api16.FrameInfo
import com.xiaocydx.performance.analyzer.frame.api24.totalNanos
import com.xiaocydx.performance.analyzer.frame.store.DroppedFramesStore
import kotlin.math.max

/**
 * @author xcc
 * @date 2025/4/7
 */
internal class FrameMetricsAggregator(
    private val receiver: FrameMetricsReceiver,
) : FrameMetricsAggregate {
    private val dropped = DroppedFramesStore(receiver.droppedFramesThreshold)
    private var startMillis = 0L
    private var totalNanos = 0f
    private var refreshRates = 0f

    override val intervalMillis = receiver.intervalMillis
    override var targetKey = 0L; private set
    override var targetName = ""; private set
    override var renderedFrames = 0; private set
    override var avgFps = 0f; private set
    override var avgRefreshRate = 0f; private set

    fun makeStart(targetKey: Long, targetName: String, isFirstDrawFrame: Boolean) {
        if (startMillis != 0L) return
        if (receiver.skipFirstFrame && isFirstDrawFrame) return
        startMillis = SystemClock.uptimeMillis()
        this.targetKey = targetKey
        this.targetName = targetName
    }

    fun accumulate(refreshRate: Float, frameInfo: FrameInfo) {
        if (startMillis == 0L) return
        val frameIntervalNanos = frameIntervalNanos(refreshRate)
        accumulateTotalNanos(frameInfo.totalNanos.toFloat(), frameIntervalNanos)
        accumulateRefreshRate(refreshRate)
        accumulateRenderedFrames()
        dropped.accumulate(frameInfo, frameIntervalNanos)
    }

    @RequiresApi(24)
    fun accumulate(refreshRate: Float, frameMetrics: FrameMetrics) {
        if (startMillis == 0L) return
        val frameIntervalNanos = frameIntervalNanos(refreshRate)
        accumulateTotalNanos(frameMetrics.totalNanos.toFloat(), frameIntervalNanos)
        accumulateRefreshRate(refreshRate)
        accumulateRenderedFrames()
        dropped.accumulate(frameMetrics, frameIntervalNanos)
    }

    fun makeEnd(ignoreIntervalMillis: Boolean) {
        if (startMillis == 0L) return
        val interval = SystemClock.uptimeMillis() - startMillis
        if (!ignoreIntervalMillis && interval < intervalMillis) return
        if (renderedFrames > 0) {
            avgFps = NANOS_PER_SECOND / (totalNanos / renderedFrames)
            avgRefreshRate = refreshRates / renderedFrames
            dropped.calculateAvg()
            receiver.onAvailable(aggregate = this)
        }
        reset()
    }

    private fun frameIntervalNanos(refreshRate: Float): Float {
        return NANOS_PER_SECOND / refreshRate
    }

    private fun accumulateTotalNanos(totalNanos: Float, frameIntervalNanos: Float) {
        this.totalNanos += max(totalNanos, frameIntervalNanos)
    }

    private fun accumulateRefreshRate(refreshRate: Float) {
        refreshRates += refreshRate
    }

    private fun accumulateRenderedFrames() {
        renderedFrames++
    }

    override fun droppedFramesOf(drop: DroppedFrames): Int {
        return dropped.framesOf(drop)
    }

    override fun avgDroppedDurationOf(drop: DroppedFrames, id: FrameDuration): Long {
        return dropped.avgDurationOf(drop, id)
    }

    override fun accept(visitor: FrameMetricsVisitor) {
        visitor.targetKey = targetKey
        visitor.targetName = targetName
        visitor.intervalMillis = intervalMillis
        visitor.renderedFrames = renderedFrames
        visitor.avgFps = avgFps
        visitor.avgRefreshRate = avgRefreshRate
        dropped.copyFramesInto(visitor.droppedFrames)
        dropped.copyAvgDurationInto(visitor.droppedDuration)
    }

    private fun reset() {
        startMillis = 0L
        totalNanos = 0f
        refreshRates = 0f
        targetKey = 0
        targetName = ""
        renderedFrames = 0
        avgFps = 0f
        avgRefreshRate = 0f
        dropped.reset()
    }
}