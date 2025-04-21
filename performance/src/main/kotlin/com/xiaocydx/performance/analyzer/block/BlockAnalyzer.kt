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
import android.os.Process
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.ProcStat
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.LooperCallback
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
        val callback = Callback(handler)
        coroutineScope.launch {
            host.needHistory(this@BlockAnalyzer)
            host.addCallback(callback)
            awaitCancellation()
        }.invokeOnCompletion {
            host.removeCallback(callback)
        }
    }

    private inner class Callback(private val handler: Handler) : LooperCallback {
        private var startMark = 0L
        private var startUptimeMillis = 0L
        private var startThreadTimeMillis = 0L

        override fun dispatch(current: DispatchContext) {
            if (current.isStart) {
                startMark = History.startMark()
                startUptimeMillis = current.uptimeMillis
                startThreadTimeMillis = current.threadTimeMillis
            } else {
                val endMark = History.endMark()
                val wallDurationMillis = current.uptimeMillis - startUptimeMillis
                val cpuDurationMillis = current.threadTimeMillis - startThreadTimeMillis
                val receivers = config.receivers
                for (i in 0 until receivers.size) {
                    if (wallDurationMillis > receivers[i].thresholdMillis) {
                        handler.post(BlockReportTask(
                            scene = current.scene.name,
                            value = current.value?.toString() ?: "",
                            lastActivity = host.getLastActivity()?.javaClass?.name ?: "",
                            startMark = startMark,
                            endMark = endMark,
                            wallDurationMillis = wallDurationMillis,
                            cpuDurationMillis = cpuDurationMillis,
                            isRecordEnabled = History.isRecordEnabled,
                            receivers = receivers
                        ))
                        break
                    }
                }
            }
        }
    }

    private class BlockReportTask(
        private val scene: String,
        private val value: String,
        private val lastActivity: String,
        private val startMark: Long,
        private val endMark: Long,
        private val wallDurationMillis: Long,
        private val cpuDurationMillis: Long,
        private val isRecordEnabled: Boolean,
        private val receivers: List<BlockReceiver>,
    ) : Runnable {

        override fun run() {
            val snapshot = History.snapshot(startMark, endMark)
            val procStat = ProcStat.get(Process.myPid())
            for (i in 0 until receivers.size) {
                val thresholdMillis = receivers[i].thresholdMillis
                if (wallDurationMillis > thresholdMillis) {
                    receivers[i].onBlock(BlockReport(
                        scene = scene,
                        value = value,
                        lastActivity = lastActivity,
                        priority = procStat.priority,
                        nice = procStat.nice,
                        snapshot = snapshot,
                        thresholdMillis = thresholdMillis,
                        wallDurationMillis = wallDurationMillis,
                        cpuDurationMillis = cpuDurationMillis,
                        isRecordEnabled = isRecordEnabled
                    ))
                }
            }
        }
    }
}