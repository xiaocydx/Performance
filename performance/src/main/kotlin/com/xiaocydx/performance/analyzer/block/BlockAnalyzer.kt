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

import android.os.SystemClock
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.Snapshot
import com.xiaocydx.performance.runtime.looper.MainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class BlockAnalyzer(
    private val host: Performance.Host,
    private val config: BlockConfig,
) : MainLooperCallback {
    private val coroutineScope = host.createMainScope()
    private var startTime = 0L
    private var startMark = 0L

    fun init() {
        coroutineScope.launch {
            host.addCallback(this@BlockAnalyzer)
            try {
                awaitCancellation()
            } finally {
                host.removeCallback(this@BlockAnalyzer)
            }
        }
    }

    override fun start(type: Type, data: Any?) {
        startTime = SystemClock.uptimeMillis()
        startMark = History.createStartMark()
    }

    override fun end(type: Type, data: Any?) {
        val endTime = SystemClock.uptimeMillis()
        val endMark = History.createEndMark()
        val time = endTime - startTime
        var snapshot: Snapshot? = null
        for (i in 0 until config.receivers.size) {
            val receiver = config.receivers[i]
            if (time <= receiver.thresholdMillis) continue
            if (snapshot == null) {
                snapshot = History.snapshot(startMark, endMark)
            }
            receiver.onReceive(scene = type.name, data = data, snapshot)
        }
    }
}