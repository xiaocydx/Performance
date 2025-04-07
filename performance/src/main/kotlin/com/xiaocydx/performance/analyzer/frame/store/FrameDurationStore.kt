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

import android.os.Build
import android.view.FrameMetrics
import android.view.FrameMetrics.ANIMATION_DURATION
import android.view.FrameMetrics.COMMAND_ISSUE_DURATION
import android.view.FrameMetrics.DRAW_DURATION
import android.view.FrameMetrics.GPU_DURATION
import android.view.FrameMetrics.INPUT_HANDLING_DURATION
import android.view.FrameMetrics.LAYOUT_MEASURE_DURATION
import android.view.FrameMetrics.SWAP_BUFFERS_DURATION
import android.view.FrameMetrics.SYNC_DURATION
import android.view.FrameMetrics.TOTAL_DURATION
import android.view.FrameMetrics.UNKNOWN_DELAY_DURATION
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.analyzer.frame.FrameDuration
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Animation
import com.xiaocydx.performance.analyzer.frame.FrameDuration.CommandIssue
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Draw
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Gpu
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Input
import com.xiaocydx.performance.analyzer.frame.FrameDuration.LayoutMeasure
import com.xiaocydx.performance.analyzer.frame.FrameDuration.SwapBuffers
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Sync
import com.xiaocydx.performance.analyzer.frame.FrameDuration.Total
import com.xiaocydx.performance.analyzer.frame.FrameDuration.UnknownDelay
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregate.Companion.NO_DURATION

/**
 * @author xcc
 * @date 2025/4/5
 */
internal class FrameDurationStore {
    private val value = LongArray(ordinalToId.size)
    private var frames = 0

    @RequiresApi(24)
    fun accumulate(frameMetrics: FrameMetrics) {
        frames++
        ordinalToId.forEachIndexed { i, id ->
            if (id == NO_ID) return@forEachIndexed
            value[i] += frameMetrics.getMetric(id)
        }
    }

    fun calculateAvg() {
        if (frames == 0) return
        value.forEachIndexed { i, duration ->
            value[i] = duration / frames
        }
    }

    fun avgOf(id: FrameDuration): Long {
        val i = id.ordinal
        if (ordinalToId[i] == NO_ID) return NO_DURATION
        return value[i]
    }

    fun reset() {
        value.fill(0)
        frames = 0
    }

    fun copyAvgTo(destination: LongArray) {
        ordinalToId.forEachIndexed { i, id ->
            destination[i] = if (id == NO_ID) NO_DURATION else value[i]
        }
    }

    private companion object {
        const val NO_ID = -1
        val ordinalToId = IntArray(FrameDuration.entries.size) { NO_ID }

        init {
            if (Build.VERSION.SDK_INT >= 24) {
                FrameDuration.entries.forEach {
                    val id = when (it) {
                        UnknownDelay -> UNKNOWN_DELAY_DURATION
                        Input -> INPUT_HANDLING_DURATION
                        Animation -> ANIMATION_DURATION
                        LayoutMeasure -> LAYOUT_MEASURE_DURATION
                        Draw -> DRAW_DURATION
                        Sync -> SYNC_DURATION
                        CommandIssue -> COMMAND_ISSUE_DURATION
                        SwapBuffers -> SWAP_BUFFERS_DURATION
                        Total -> TOTAL_DURATION
                        else -> return@forEach
                    }
                    ordinalToId[it.ordinal] = id
                }
            } else {
                ordinalToId[Input.ordinal] = 0
                ordinalToId[Animation.ordinal] = 1
                ordinalToId[LayoutMeasure.ordinal] = 2
            }

            if (Build.VERSION.SDK_INT >= 31) {
                ordinalToId[Gpu.ordinal] = GPU_DURATION
            }
        }
    }
}