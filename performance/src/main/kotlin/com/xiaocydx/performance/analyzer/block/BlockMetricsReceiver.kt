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

package com.xiaocydx.performance.analyzer.block

import androidx.annotation.IntRange

/**
 * [BlockMetrics]的接收者
 *
 * @author xcc
 * @date 2025/4/15
 */
interface BlockMetricsReceiver {

    /**
     * 接收[BlockMetrics]的卡顿阈值
     */
    @get:IntRange(from = 1)
    val thresholdMillis: Long
        get() = DEFAULT_THRESHOLD_MILLIS

    /**
     * 执行时间超过[thresholdMillis]，接收[BlockMetrics]
     *
     * **注意**：该函数不能执行耗时较长的逻辑（比如IO操作），这会导致[metrics]不准确。
     */
    fun receive(metrics: BlockMetrics)

    companion object {
        const val DEFAULT_THRESHOLD_MILLIS = 700L
    }
}