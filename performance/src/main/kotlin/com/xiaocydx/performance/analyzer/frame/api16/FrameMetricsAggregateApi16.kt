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

package com.xiaocydx.performance.analyzer.frame.api16

import com.xiaocydx.performance.analyzer.frame.DroppedFrames
import com.xiaocydx.performance.analyzer.frame.FrameDuration
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregate
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregateVisitor

/**
 * @author xcc
 * @date 2025/4/5
 */
internal class FrameMetricsAggregateApi16 : FrameMetricsAggregate {
    override val targetKey = 0L
    override val targetName = ""
    override val intervalMillis = 0L
    override val renderedFrames = 0
    override val avgFps = 0f
    override val avgRefreshRate = 0f

    override fun droppedFramesOf(drop: DroppedFrames) = 0

    override fun avgDroppedDurationOf(drop: DroppedFrames, id: FrameDuration) = 0L

    override fun accept(visitor: FrameMetricsAggregateVisitor) = Unit
}