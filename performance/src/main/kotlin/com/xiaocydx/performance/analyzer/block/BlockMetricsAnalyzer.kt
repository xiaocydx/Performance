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

package com.xiaocydx.performance.analyzer.block

import android.os.Handler
import android.os.Process
import android.os.SystemClock
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.record.Snapshot
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
internal class BlockMetricsAnalyzer(
    host: Host,
    private val config: BlockMetricsConfig
) : Analyzer(host) {

    override fun init() {
        val handler = Handler(host.dumpLooper)
        val callback = Callback(handler)
        coroutineScope.launch {
            host.registerHistory(this@BlockMetricsAnalyzer)
            host.addCallback(callback)
            awaitCancellation()
        }.invokeOnCompletion {
            host.removeCallback(callback)
            host.unregisterHistory(this@BlockMetricsAnalyzer)
        }
    }

    private inner class Callback(private val handler: Handler) : LooperCallback {
        private var startMark = 0L
        private var startUptimeMillis = 0L
        private var startThreadTimeMillis = 0L

        override fun dispatch(current: DispatchContext) {
            when (current) {
                is Start -> {
                    startMark = current.mark
                    startUptimeMillis = current.uptimeMillis
                    startThreadTimeMillis = current.threadTimeMillis
                }
                is End -> {
                    if (current.uptimeMillis - startUptimeMillis <= config.thresholdMillis) return
                    val latestActivity = host.getLatestActivity()?.javaClass?.name
                    if (latestActivity.isNullOrEmpty()) return
                    handler.post(BlockTask(
                        startMark = startMark,
                        endMark = current.mark,
                        intermediate = BlockMetrics(
                            //region lack
                            pid = 0,
                            tid = 0,
                            createTimeMillis = 0L,
                            snapshot = Snapshot.empty(),
                            sampleList = emptyList(),
                            //endregion
                            scene = current.scene.toString(),
                            metadata = current.metadata.toString(),
                            latestActivity = latestActivity,
                            thresholdMillis = config.thresholdMillis,
                            startUptimeMillis = startUptimeMillis,
                            startThreadTimeMillis = startThreadTimeMillis,
                            endUptimeMillis = current.uptimeMillis,
                            endThreadTimeMillis = SystemClock.currentThreadTimeMillis(),
                            isRecordEnabled = host.isRecordEnabled
                        )
                    ))
                }
            }
        }
    }

    private inner class BlockTask(
        private val startMark: Long,
        private val endMark: Long,
        private val intermediate: BlockMetrics
    ) : Runnable {

        override fun run() {
            val createTimeMillis = System.currentTimeMillis()
            val startUptimeMillis = intermediate.startUptimeMillis
            val endUptimeMillis = intermediate.endUptimeMillis
            val snapshot = host.snapshot(startMark, endMark).available(endUptimeMillis)
            val sampleList = host.sampleList(startUptimeMillis, endUptimeMillis)
            val metrics = intermediate.copy(
                pid = host.pid,
                tid = host.pid, // 主线程的tid跟pid一致
                createTimeMillis = createTimeMillis,
                snapshot = snapshot,
                sampleList = sampleList
            )
            config.receivers.forEach { it.receive(metrics) }
        }
    }
}