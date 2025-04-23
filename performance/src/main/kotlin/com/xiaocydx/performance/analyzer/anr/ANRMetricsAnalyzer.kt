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
internal class ANRMetricsAnalyzer(host: Performance.Host) : Analyzer(host) {

    override fun init() {
        var watchDog: ANRWatchDog? = null
        val chain = DispatchChain(capacity = 2000)
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

    private class Callback(private val chain: DispatchChain) : LooperCallback {
        override fun dispatch(current: DispatchContext) {
            when (current) {
                is Start -> {
                }
                is End -> {
                    chain.add(current.scene, current.metadata.toString())
                }
            }
        }
    }
}