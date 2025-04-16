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

@file:Suppress("ReplaceManualRangeWithIndicesCalls")

package com.xiaocydx.performance.analyzer.block

import android.os.Handler
import android.os.SystemClock
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.looper.MainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class BlockAnalyzer(
    host: Performance.Host,
    private val config: BlockConfig
) : Analyzer(host) {

    override fun init() {
        val handler = Handler(host.dumpLooper)
        val callback = Callback(handler, config)
        coroutineScope.launch {
            host.needHistory(this@BlockAnalyzer)
            host.addCallback(callback)
            awaitCancellation()
        }.invokeOnCompletion {
            host.removeCallback(callback)
        }
    }

    private class Callback(
        private val handler: Handler,
        private val config: BlockConfig,
    ) : MainLooperCallback {
        private var startMark = 0L
        private var startMillis = 0L

        override fun start(type: Type, data: Any?) {
            startMark = History.startMark()
            startMillis = SystemClock.uptimeMillis()
        }

        override fun end(type: Type, data: Any?) {
            val endMillis = SystemClock.uptimeMillis()
            val endMark = History.endMark()
            val durationMillis = endMillis - startMillis
            val receivers = config.receivers
            for (i in 0 until receivers.size) {
                if (durationMillis > receivers[i].thresholdMillis) {
                    handler.post(DumpTask(
                        scene = type.name,
                        startMark = startMark,
                        endMark = endMark,
                        durationMillis = durationMillis,
                        isRecordEnabled = History.isRecordEnabled,
                        receivers = receivers
                    ))
                    break
                }
            }
        }
    }

    private class DumpTask(
        private val scene: String,
        private val startMark: Long,
        private val endMark: Long,
        private val durationMillis: Long,
        private val isRecordEnabled: Boolean,
        private val receivers: List<BlockReceiver>,
    ) : Runnable {

        override fun run() {
            val snapshot = History.snapshot(startMark, endMark)
            for (i in 0 until receivers.size) {
                val thresholdMillis = receivers[i].thresholdMillis
                if (durationMillis > thresholdMillis) {
                    receivers[i].onBlock(BlockReport(
                        scene = scene,
                        snapshot = snapshot,
                        durationMillis = durationMillis,
                        thresholdMillis = thresholdMillis,
                        isRecordEnabled = isRecordEnabled
                    ))
                }
            }
        }
    }
}