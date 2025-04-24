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
interface ANRMetricsReceiver {

    @get:IntRange(from = 0)
    val idleThresholdMillis: Long
        get() = DEFAULT_IDLE_THRESHOLD_MILLIS

    @get:IntRange(from = 0)
    val mergeThresholdMillis: Long
        get() = DEFAULT_MERGE_THRESHOLD_MILLIS

    companion object {
        const val DEFAULT_IDLE_THRESHOLD_MILLIS = 16L
        const val DEFAULT_MERGE_THRESHOLD_MILLIS = 300L
    }
}