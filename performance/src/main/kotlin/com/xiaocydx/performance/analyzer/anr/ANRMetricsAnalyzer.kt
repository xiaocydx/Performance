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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package com.xiaocydx.performance.analyzer.anr

import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.history.segment.Segment
import com.xiaocydx.performance.runtime.history.segment.collectFrom
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class ANRMetricsAnalyzer(
    host: Host,
    private val config: ANRMetricsConfig
) : Analyzer(host) {

    override fun init() {
        coroutineScope.launch {
            host.registerHistory(this@ANRMetricsAnalyzer)
            val callback = Callback(host.merger(
                idleThresholdMillis = config.receiver.idleThresholdMillis,
                mergeThresholdMillis = config.receiver.mergeThresholdMillis
            ))
            host.addCallback(callback)
            val watchDog = ANRWatchDog(host.ams)
            watchDog.start()
            try {
                awaitCancellation()
            } finally {
                watchDog.interrupt()
                host.removeCallback(callback)
            }
        }
    }

    private inner class Callback(private val merger: Merger) : LooperCallback {
        private val segment = Segment()

        override fun dispatch(current: DispatchContext) {
            // collectFrom() time << 1ms
            segment.collectFrom(current)
            if (current is End) {
                merger.consume(segment)
            }
        }
    }
}