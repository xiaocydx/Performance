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

package com.xiaocydx.performance.analyzer.frame.api24

import android.view.FrameMetrics
import androidx.annotation.RequiresApi

@get:RequiresApi(24)
internal inline val FrameMetrics.unknownDelayNanos: Long
    get() = getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.inputNanos: Long
    get() = getMetric(FrameMetrics.INPUT_HANDLING_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.animationNanos: Long
    get() = getMetric(FrameMetrics.ANIMATION_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.layoutMeasureNanos: Long
    get() = getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.drawNanos: Long
    get() = getMetric(FrameMetrics.DRAW_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.syncNanos: Long
    get() = getMetric(FrameMetrics.SYNC_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.commandIssueNanos: Long
    get() = getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.swapBuffersNanos: Long
    get() = getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.totalNanos: Long
    get() = getMetric(FrameMetrics.TOTAL_DURATION)

@get:RequiresApi(24)
internal inline val FrameMetrics.isFirstDrawFrame: Boolean
    get() = getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L

@get:RequiresApi(26)
internal inline val FrameMetrics.intendedVsyncTimestamp: Long
    get() = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)

@get:RequiresApi(26)
internal inline val FrameMetrics.vsyncTimestamp: Long
    get() = getMetric(FrameMetrics.VSYNC_TIMESTAMP)

@get:RequiresApi(31)
internal inline val FrameMetrics.gpuNanos: Long
    get() = getMetric(FrameMetrics.GPU_DURATION)

@get:RequiresApi(31)
internal inline val FrameMetrics.deadline: Long
    get() = getMetric(FrameMetrics.DEADLINE)