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

import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.SampleData
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.Start
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class ANRMetricsAnalyzer(
    host: Performance.Host,
    private val config: ANRMetricsConfig
) : Analyzer(host) {

    override fun init() {
        var watchDog: ANRWatchDog? = null
        val chain = ANRMetricsChain(
            capacity = 5000,
            idleThresholdMillis = config.receiver.idleThresholdMillis,
            mergeThresholdMillis = config.receiver.mergeThresholdMillis
        )
        val callback = Callback(chain)
        coroutineScope.launch {
            host.requireHistory(this@ANRMetricsAnalyzer)
            host.addCallback(callback)
            watchDog = ANRWatchDog(host.ams)
            watchDog!!.start()
            awaitCancellation()
        }.invokeOnCompletion {
            host.removeCallback(callback)
            watchDog?.interrupt()
            watchDog = null
            chain.clear()
        }
    }

    private class Callback(private val chain: ANRMetricsChain) : LooperCallback {
        private var startMark = 0L
        private var startUptimeMillis = 0L
        @Volatile private var sampleData: SampleData? = null

        private fun consumeSampleData(): SampleData? {
            val sampleData = sampleData ?: return null
            this.sampleData = null
            return sampleData
        }

        override fun dispatch(current: DispatchContext) {
            when (current) {
                is Start -> {
                    startMark = current.mark
                    startUptimeMillis = current.uptimeMillis
                }
                // TODO: 优化metadata的记录方式
                is End -> {
                    val sampleData = consumeSampleData()
                    chain.append(
                        isSingle = current.isFromActivityThread || sampleData != null,
                        scene = current.scene,
                        metadata = current.metadata.toString(),
                        startMark = startMark,
                        endMark = current.mark,
                        startUptimeMillis = startUptimeMillis,
                        endUptimeMillis = current.uptimeMillis,
                        sampleData = sampleData
                    )
                }
            }
        }
    }
}