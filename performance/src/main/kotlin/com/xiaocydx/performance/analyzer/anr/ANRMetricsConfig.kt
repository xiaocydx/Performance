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

package com.xiaocydx.performance.analyzer.anr

import androidx.annotation.IntRange

/**
 * @author xcc
 * @date 2025/4/24
 */
data class ANRMetricsConfig(
    @get:IntRange(from = 0)
    val idleThresholdMillis: Long = 16L,
    @get:IntRange(from = 0)
    val mergeThresholdMillis: Long = 300L,
    val receivers: List<ANRMetricsReceiver> = emptyList()
) {

    internal fun checkProperty() {
        require(idleThresholdMillis >= 0) { "idleThresholdMillis < 0" }
        require(mergeThresholdMillis >= 0) { "mergeThresholdMillis < 0" }
    }
}