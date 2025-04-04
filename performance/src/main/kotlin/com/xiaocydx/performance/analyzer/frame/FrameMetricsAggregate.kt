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
interface FrameMetricsAggregate {

    val targetName: String

    @get:IntRange(from = 0)
    val intervalMillis: Long

    @get:IntRange(from = 0)
    val renderedFrames: Int

    @get:FloatRange(from = 0.0)
    val avgFps: Float

    @get:FloatRange(from = 0.0)
    val avgRefreshRate: Float

    @get:IntRange(from = 0)
    val droppedFrames: Int

    @IntRange(from = 0)
    fun getDroppedFrames(level: DropLevel): Int

    companion object {
        const val NANOS_PER_MILLIS = 1000000
        const val NANOS_PER_SECOND = 1000000000
    }
}

enum class DropLevel {
    Best, Normal, Middle, High, Frozen;

    data class Threshold(
        @IntRange(from = 0) val best: Int = 0,
        @IntRange(from = 0) val normal: Int = 3,
        @IntRange(from = 0) val middle: Int = 9,
        @IntRange(from = 0) val high: Int = 24,
        @IntRange(from = 0) val frozen: Int = 42,
    )
}

fun FrameMetricsAggregate.copy(): FrameMetricsAggregate {
    val dropLevelFrames = IntArray(DropLevel.entries.size)
    DropLevel.entries.forEach { dropLevelFrames[it.ordinal] = getDroppedFrames(it) }
    return ReadOnlyFrameMetricsAggregate(
        targetName = targetName,
        intervalMillis = intervalMillis,
        renderedFrames = renderedFrames,
        avgFps = avgFps,
        avgRefreshRate = avgRefreshRate,
        droppedFrames = droppedFrames,
        dropLevelFrames = dropLevelFrames
    )
}

private class ReadOnlyFrameMetricsAggregate(
    override val targetName: String,
    override val intervalMillis: Long,
    override val renderedFrames: Int,
    override val avgFps: Float,
    override val avgRefreshRate: Float,
    override val droppedFrames: Int,
    private val dropLevelFrames: IntArray
) : FrameMetricsAggregate {

    override fun getDroppedFrames(level: DropLevel): Int {
        return dropLevelFrames[level.ordinal]
    }
}