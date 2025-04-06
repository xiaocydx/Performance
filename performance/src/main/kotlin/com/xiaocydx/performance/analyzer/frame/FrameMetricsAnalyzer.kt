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

import android.os.Build
import com.xiaocydx.performance.Cancellable
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.api16.FrameMetricsAnalyzerApi16
import com.xiaocydx.performance.analyzer.frame.api24.FrameMetricsAnalyzerApi24

/**
 * @author xcc
 * @date 2025/4/5
 */
internal interface FrameMetricsAnalyzer : Cancellable {

    fun init()

    override fun cancel()

    companion object {

        fun create(
            host: Performance.Host,
            config: FrameMetricsConfig
        ) = if (Build.VERSION.SDK_INT >= 24) {
            FrameMetricsAnalyzerApi24(host, config)
        } else {
            FrameMetricsAnalyzerApi16()
        }
    }
}