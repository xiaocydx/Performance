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

import androidx.annotation.FloatRange
import androidx.annotation.IntRange

/**
 * @author xcc
 * @date 2025/4/4
 */
interface FrameMetricsAccumulation {

    val targetName: String

    @get:IntRange(from = 0)
    val intervalMillis: Long

    @get:IntRange(from = 0)
    val totalFrames: Int

    @get:FloatRange(from = 0.0)
    val droppedFrames: Float

    @get:FloatRange(from = 0.0)
    val avgFps: Float

    @get:FloatRange(from = 0.0)
    val avgDroppedFrames: Float

    @get:FloatRange(from = 0.0)
    val avgRefreshRate: Float
}

fun FrameMetricsAccumulation.copy(): FrameMetricsAccumulation {
    val readOnly = this as? ReadOnlyFrameMetricsAccumulation
    if (readOnly != null) return readOnly.copy()
    return ReadOnlyFrameMetricsAccumulation(
        targetName = targetName,
        intervalMillis = intervalMillis,
        totalFrames = totalFrames,
        droppedFrames = droppedFrames,
        avgFps = avgFps,
        avgDroppedFrames = avgDroppedFrames,
        avgRefreshRate = avgRefreshRate
    )
}

internal data class ReadOnlyFrameMetricsAccumulation(
    override val targetName: String,
    override val intervalMillis: Long,
    override val totalFrames: Int,
    override val droppedFrames: Float,
    override val avgFps: Float,
    override val avgDroppedFrames: Float,
    override val avgRefreshRate: Float
) : FrameMetricsAccumulation