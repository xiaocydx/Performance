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

package com.xiaocydx.performance.analyzer.block

import android.os.Handler
import android.os.SystemClock
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.looper.MainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class BlockAnalyzer(
    private val host: Performance.Host,
    private val config: BlockConfig,
) : Analyzer, MainLooperCallback {
    private val coroutineScope = host.createMainScope()
    private val handler = Handler(host.dumpLooper)
    private var startMs = 0L
    private var startMark = 0L

    override fun init() {
        coroutineScope.launch {
            host.addCallback(this@BlockAnalyzer)
            try {
                awaitCancellation()
            } finally {
                host.removeCallback(this@BlockAnalyzer)
            }
        }
    }

    override fun cancel() {
        coroutineScope.cancel()
    }

    override fun start(type: Type, data: Any?) {
        startMs = SystemClock.uptimeMillis()
        startMark = History.startMark()
    }

    override fun end(type: Type, data: Any?) {
        val endMs = SystemClock.uptimeMillis()
        val endMark = History.endMark()
        val timeMs = endMs - startMs
        for (i in 0 until config.receivers.size) {
            val receiver = config.receivers[i]
            if (timeMs > receiver.thresholdMillis) {
                handler.post(DumpTask(scene = type.name, startMark, endMark, timeMs, config))
                break
            }
        }
    }

    private class DumpTask(
        private val scene: String,
        private val startMark: Long,
        private val endMark: Long,
        private val timeMs: Long,
        private val config: BlockConfig
    ) : Runnable {

        override fun run() {
            val snapshot = History.snapshot(startMark, endMark)
            if (!snapshot.isAvailable) return
            for (i in 0 until config.receivers.size) {
                val receiver = config.receivers[i]
                if (timeMs > receiver.thresholdMillis) {
                    receiver.onReceive(scene, snapshot)
                }
            }
        }
    }
}