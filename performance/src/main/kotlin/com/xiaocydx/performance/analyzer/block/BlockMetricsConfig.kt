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
 * @author xcc
 * @date 2025/4/15
 */
data class BlockMetricsConfig(
    @IntRange(from = 1)
    val blockThresholdMillis: Long = 700,
    @IntRange(from = 1)
    val sampleIntervalMillis: Long = 500,
    val receivers: List<BlockMetricsReceiver> = emptyList()
) {

    internal fun checkProperty() {
        require(blockThresholdMillis >= 1) { "blockThresholdMillis < 1" }
        require(sampleIntervalMillis >= 1) { "sampleIntervalMillis < 1" }
    }
}