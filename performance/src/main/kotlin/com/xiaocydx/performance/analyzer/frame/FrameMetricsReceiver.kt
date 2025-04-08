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

import androidx.annotation.IntRange

/**
 * [FrameMetricsAggregate]的接收者
 *
 * @author xcc
 * @date 2025/4/3
 */
interface FrameMetricsReceiver {

    /**
     * 接收[FrameMetricsAggregate]的时间间隔
     */
    @get:IntRange(from = 0)
    val intervalMillis: Long
        get() = DEFAULT_INTERVAL_MILLIS

    /**
     * 是否跳过视图树的首帧，不做统计
     *
     * **注意**：首帧通常耗时较长，会被划分进[FrameMetricsAggregate.droppedFramesOf]。
     */
    val skipFirstFrame: Boolean
        get() = true

    /**
     * [DroppedFrames]划分等级的阈值，阈值的解释可以看[DroppedFrames.Threshold]的注释
     */
    val droppedFramesThreshold: DroppedFrames.Threshold
        get() = DefaultDroppedFramesThreshold

    /**
     * 达到[intervalMillis]，接收可用的[FrameMetricsAggregate]
     *
     * **注意**：
     * 1. 该函数不能执行耗时较长的逻辑（比如IO操作），这会导致[aggregate]不准确。
     * 2. 只能在该函数下使用[aggregate]，若需要在其他线程处理[aggregate]的数据，
     * 避免第1点的影响，则使用[FrameMetricsVisitor] copy [aggregate]的数据，
     * 或者调用[FrameMetricsAggregate.copy]。
     *
     * [FrameMetricsVisitor]的使用可以参考[FrameMetricsPrinter]。
     */
    fun onAvailable(aggregate: FrameMetricsAggregate)

    companion object {
        private val DefaultDroppedFramesThreshold = DroppedFrames.Threshold()
        const val DEFAULT_INTERVAL_MILLIS = 5 * 1000L
    }
}