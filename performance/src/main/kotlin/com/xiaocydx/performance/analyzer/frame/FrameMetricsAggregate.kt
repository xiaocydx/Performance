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
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.analyzer.frame.DroppedFrames.Total

/**
 * 帧指标数据的聚合
 *
 * @author xcc
 * @date 2025/4/4
 */
interface FrameMetricsAggregate {

    /**
     * 目标Key，目前只有Activity，`targetKey = activity.hashCode()`
     */
    val targetKey: Long

    /**
     * 目标名称，目前只有Activity，`targetName = activity.javaClass.simpleName`
     */
    val targetName: String

    /**
     * 聚合时间间隔，由[FrameMetricsReceiver]提供
     */
    @get:IntRange(from = 0)
    val intervalMillis: Long

    /**
     * 完成渲染的总帧数，参与[avgFps]的计算
     */
    @get:IntRange(from = 0)
    val renderedFrames: Int

    /**
     * 平均FPS
     *
     * 该属性数值小，不代表不流畅，需结合[droppedFramesOf]和[avgDroppedDurationOf]做进一步分析。
     */
    @get:FloatRange(from = 0.0)
    val avgFps: Float

    /**
     * 平均刷新率
     *
     * 部分设备会有动态刷新率，该属性是[intervalMillis]期间内的平均刷新率。
     */
    @get:FloatRange(from = 0.0)
    val avgRefreshRate: Float

    /**
     * 获取[drop]的丢帧数，丢帧的解释可以看[DroppedFrames]的注释
     */
    @IntRange(from = 0)
    fun droppedFramesOf(drop: DroppedFrames): Int

    /**
     * 获取[drop]中[FrameDuration]的执行时长
     *
     * ```
     * val nanoseconds = avgDroppedDurationOf(DroppedFrames.Best, FrameDuration.Input)
     * val millisecond = nanoseconds / NANOS_PER_MILLIS
     * ```
     *
     * @return `Build.VERSION.SDK_INT < id.api` - [NO_DURATION]
     */
    @IntRange(from = NO_DURATION)
    fun avgDroppedDurationOf(drop: DroppedFrames, id: FrameDuration): Long

    /**
     * [visitor]访问内部数据，完成copy
     */
    fun accept(visitor: FrameMetricsAggregateVisitor)

    companion object {
        const val NO_DURATION = -1L
        const val NANOS_PER_MILLIS = 1000000
        const val NANOS_PER_SECOND = 1000000000
    }
}

/**
 * [Total]表示总丢帧数，其他枚举值表示丢帧等级的划分
 *
 * 若一帧的执行时长超过Vsync间隔时长，则视为丢帧，丢帧数量的计算：
 * ```
 * // frameTotalMillis = 34ms
 * // frameIntervalMillis = 16ms
 * // droppedFrames = 2
 * val droppedFrames = frameTotalMillis / frameIntervalMillis
 * ```
 */
enum class DroppedFrames {
    Total, Best, Normal, Middle, High, Frozen;

    /**
     * 丢帧等级的阈值：
     * ```
     * bestRange = [best, normal)
     * normalRange = [normal, middle)
     * middleRange = [middle, high)
     * highRange = [high, frozen)
     * frozenRange = [frozen, +∞)
     * ```
     */
    data class Threshold(
        @IntRange(from = 0) val best: Int = 0,
        @IntRange(from = 0) val normal: Int = 3,
        @IntRange(from = 0) val middle: Int = 9,
        @IntRange(from = 0) val high: Int = 24,
        @IntRange(from = 0) val frozen: Int = 42,
    )
}

enum class FrameDuration(val api: Int) {
    @RequiresApi(24)
    UnknownDelay(api = 24),

    Input(api = 16),

    Animation(api = 16),

    LayoutMeasure(api = 16),

    @RequiresApi(24)
    Draw(api = 24),

    @RequiresApi(24)
    Sync(api = 24),

    @RequiresApi(24)
    CommandIssue(api = 24),

    @RequiresApi(24)
    SwapBuffers(api = 24),

    @RequiresApi(24)
    Total(api = 24),

    @RequiresApi(31)
    Gpu(api = 31)
}

fun FrameMetricsAggregate.copy(): FrameMetricsAggregate {
    return FrameMetricsAggregateVisitor().apply(::accept)
}

class FrameMetricsAggregateVisitor : FrameMetricsAggregate {
    private val droppedSize = DroppedFrames.entries.size
    internal val droppedFrames = IntArray(droppedSize)
    internal val droppedDuration = Array(droppedSize) { LongArray(FrameDuration.entries.size) }

    override var targetKey = 0L; internal set
    override var targetName = ""; internal set
    override var intervalMillis = 0L; internal set
    override var renderedFrames = 0; internal set
    override var avgFps = 0f; internal set
    override var avgRefreshRate = 0f; internal set

    override fun droppedFramesOf(drop: DroppedFrames): Int {
        return droppedFrames[drop.ordinal]
    }

    override fun avgDroppedDurationOf(drop: DroppedFrames, id: FrameDuration): Long {
        return droppedDuration[drop.ordinal][id.ordinal]
    }

    override fun accept(visitor: FrameMetricsAggregateVisitor) {
        visitor.targetKey = targetKey
        visitor.targetName = targetName
        visitor.intervalMillis = intervalMillis
        visitor.renderedFrames = renderedFrames
        visitor.avgFps = avgFps
        visitor.avgRefreshRate = avgRefreshRate
        droppedFrames.copyInto(visitor.droppedFrames)
        droppedDuration.forEachIndexed { i, duration ->
            duration.copyInto(visitor.droppedDuration[i])
        }
    }
}