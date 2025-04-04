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
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregate.Companion.NANOS_PER_SECOND
import kotlin.math.max

/**
 * @author xcc
 * @date 2025/4/4
 */
@WorkerThread
@RequiresApi(24)
internal class FrameMetricsAggregator(
    private val receiver: FrameMetricsReceiver
) : FrameMetricsAggregate {
    private val dropped = Dropped(receiver.dropLevelThreshold)
    private var startMillis = 0L
    private var totalNanos = 0f
    private var refreshRates = 0f

    override val intervalMillis = receiver.intervalMillis
    override val droppedFrames get() = dropped.droppedFrames
    override var targetName = ""; private set
    override var renderedFrames = 0; private set
    override var avgFps = 0f; private set
    override var avgRefreshRate = 0f; private set

    override fun getDroppedFrames(level: DropLevel): Int {
        return dropped.getDroppedFrames(level)
    }

    fun makeStart(targetName: String, frameMetrics: FrameMetrics) {
        if (startMillis != 0L) return
        if (receiver.skipFirstFrame && frameMetrics.isFirstDrawFrame) return
        startMillis = SystemClock.uptimeMillis()
        this.targetName = targetName
    }

    fun accumulate(refreshRate: Float, frameMetrics: FrameMetrics) {
        if (startMillis == 0L) return
        val frameIntervalNanos = NANOS_PER_SECOND / refreshRate
        totalNanos += max(frameMetrics.totalNanos.toFloat(), frameIntervalNanos)
        refreshRates += refreshRate
        renderedFrames++
        dropped.accumulate(frameMetrics.totalNanos, frameIntervalNanos)
    }

    fun makeEnd(ignoreIntervalMillis: Boolean) {
        if (startMillis == 0L || renderedFrames == 0) return
        val interval = SystemClock.uptimeMillis() - startMillis
        if (!ignoreIntervalMillis && interval < intervalMillis) return
        // TODO: 过滤两帧间隔较长的情况
        avgFps = NANOS_PER_SECOND / (totalNanos / renderedFrames)
        avgRefreshRate = refreshRates / renderedFrames
        receiver.onAvailable(aggregate = this)
        reset()
    }

    private fun reset() {
        startMillis = 0L
        totalNanos = 0f
        refreshRates = 0f
        targetName = ""
        renderedFrames = 0
        avgFps = 0f
        avgRefreshRate = 0f
        dropped.rest()
    }

    private class Dropped(threshold: DropLevel.Threshold) {
        private val levelLast = DropLevel.entries.size - 1
        private val levelFrames = IntArray(levelLast + 1)
        private val levelThreshold = IntArray(levelLast + 1)
        var droppedFrames = 0; private set

        init {
            levelThreshold[DropLevel.Best.ordinal] = threshold.best
            levelThreshold[DropLevel.Normal.ordinal] = threshold.normal
            levelThreshold[DropLevel.Middle.ordinal] = threshold.middle
            levelThreshold[DropLevel.High.ordinal] = threshold.high
            levelThreshold[DropLevel.Frozen.ordinal] = threshold.frozen
        }

        fun getDroppedFrames(level: DropLevel): Int {
            return levelFrames[level.ordinal]
        }

        fun accumulate(totalNanos: Long, frameIntervalNanos: Float) {
            val fraction = totalNanos.toFloat() / frameIntervalNanos
            val frames = fraction.toInt()
            // TODO: 补充超出的耗时累积
            droppedFrames += frames
            for (i in levelLast downTo 0) {
                if (frames >= levelThreshold[i]) {
                    levelFrames[i] += frames
                    break
                }
            }
        }

        fun rest() {
            droppedFrames = 0
            levelFrames.fill(0)
        }
    }
}