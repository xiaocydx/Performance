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

import android.app.Activity
import android.os.Handler
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.log
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/3
 */
@RequiresApi(24)
internal class FrameMetricsAnalyzerApi24(
    host: Performance.Host,
    private val config: FrameMetricsConfig,
) : FrameMetricsAnalyzer(host) {
    private val frameMetricsHandler = Handler(host.defaultLooper)

    override fun createListener(activity: Activity): FrameMetricsListener {
        return FrameMetricsListenerImpl(activity)
    }

    private inner class FrameMetricsListenerImpl(activity: Activity) :
            FrameMetricsListener(activity, config),
            Window.OnFrameMetricsAvailableListener {

        @MainThread
        override fun attach() = apply {
            val window = activityRef.get()?.window ?: return@apply
            window.addOnFrameMetricsAvailableListener(this, frameMetricsHandler)
        }

        @MainThread
        override fun detach() = apply {
            activityRef.get()?.window?.removeOnFrameMetricsAvailableListener(this)
            forceMakeEnd()
        }

        @MainThread
        override fun forceMakeEnd() = apply {
            frameMetricsHandler.post { dispatchAggregators { it.makeEnd(ignoreIntervalMillis = true) } }
        }

        @WorkerThread
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int,
        ) {
            val refreshRate = window.getRefreshRate(defaultRefreshRate)
            val isFirstDrawFrame = frameMetrics.isFirstDrawFrame
            val timeMillis = measureTimeMillis {
                dispatchAggregators { it.makeStart(activityKey, activityName, isFirstDrawFrame) }
                dispatchAggregators { it.accumulate(refreshRate, frameMetrics) }
                dispatchAggregators { it.makeEnd(ignoreIntervalMillis = false) }
            }
            if (timeMillis > 1) {
                log { "FrameMetricsListener dispatchAggregators timeMillis = $timeMillis" }
            }
        }
    }
}