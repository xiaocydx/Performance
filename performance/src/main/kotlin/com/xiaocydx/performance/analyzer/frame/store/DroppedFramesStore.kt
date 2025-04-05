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

package com.xiaocydx.performance.analyzer.frame.store

import android.view.FrameMetrics
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.analyzer.frame.DroppedFrames
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Best
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Frozen
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.High
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Middle
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Normal
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Threshold
import com.xiaocydx.performance.analyzer.frame.FrameDuration
import com.xiaocydx.performance.analyzer.frame.api24.totalNanos

/**
 * @author xcc
 * @date 2025/4/5
 */
internal class DroppedFramesStore(threshold: Threshold) {
    private val size = DroppedFrames.entries.size
    private val total = DroppedFrames.Total.ordinal
    private val last = size - 1
    private val value = IntArray(size)
    private val threshold = IntArray(size)
    private val durations = Array(size) { FrameDurationStore() }

    init {
        assert(total == 0)
        this.threshold[Best.ordinal] = threshold.best
        this.threshold[Normal.ordinal] = threshold.normal
        this.threshold[Middle.ordinal] = threshold.middle
        this.threshold[High.ordinal] = threshold.high
        this.threshold[Frozen.ordinal] = threshold.frozen
    }

    @RequiresApi(24)
    fun accumulate(frameMetrics: FrameMetrics, frameIntervalNanos: Float) {
        val frames = (frameMetrics.totalNanos / frameIntervalNanos).toInt()
        if (frames < 1) return
        var i = last
        while (i > total) {
            if (frames >= threshold[i]) {
                value[i] += frames
                durations[i].accumulate(frameMetrics)
                break
            }
            i--
        }
        value[total] += frames
        durations[total].accumulate(frameMetrics)
    }

    fun calculateAvg() {
        durations.forEach { it.calculateAvg() }
    }

    fun framesOf(drop: DroppedFrames): Int {
        return value[drop.ordinal]
    }

    fun avgDurationOf(drop: DroppedFrames, id: FrameDuration): Long {
        return durations[drop.ordinal].avgOf(id)
    }

    fun copyFramesInto(destination: IntArray) {
        value.copyInto(destination)
    }

    fun copyAvgDurationInto(destination: Array<LongArray>) {
        durations.forEachIndexed { i, store -> store.copyAvgTo(destination[i]) }
    }

    fun reset() {
        value.fill(0)
        durations.forEach { it.reset() }
    }
}