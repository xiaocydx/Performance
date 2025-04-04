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
import androidx.annotation.WorkerThread

/**
 * @author xcc
 * @date 2025/4/3
 */
interface FrameMetricsReceiver {

    @get:IntRange(from = 0)
    val intervalMillis: Long
        get() = DEFAULT_INTERVAL_MILLIS

    val skipFirstFrame: Boolean
        get() = true

    val dropLevelThreshold: DropLevel.Threshold
        get() = DefaultDropLevelThreshold

    @WorkerThread
    fun onAvailable(aggregate: FrameMetricsAggregate)

    companion object {
        private val DefaultDropLevelThreshold = DropLevel.Threshold()
        const val DEFAULT_INTERVAL_MILLIS = 5 * 1000L
    }
}