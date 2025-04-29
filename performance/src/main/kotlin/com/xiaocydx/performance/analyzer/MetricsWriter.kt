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

package com.xiaocydx.performance.analyzer

import android.app.Application
import com.xiaocydx.performance.analyzer.anr.ANRMetrics
import com.xiaocydx.performance.analyzer.anr.ANRMetricsReceiver
import com.xiaocydx.performance.analyzer.block.BlockMetrics
import com.xiaocydx.performance.analyzer.block.BlockMetricsReceiver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 往设备写入[Metrics]
 *
 * @author xcc
 * @date 2025/4/29
 */
class MetricsWriter(application: Application) : BlockMetricsReceiver, ANRMetricsReceiver {
    private val output by lazy { MetricsOutput(application) }

    override fun receive(metrics: BlockMetrics) {
        dispatch { output.write(dirName = "block", metrics) }
    }

    override fun receive(metrics: ANRMetrics) {
        dispatch { output.write(dirName = "anr", metrics) }
    }

    private fun dispatch(action: () -> Unit) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext, action)
    }
}