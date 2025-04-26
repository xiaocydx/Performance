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
import android.os.SystemClock
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.sample.ProcStat
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
        }
    }

    private inner class Callback(private val handler: Handler) : LooperCallback {
        private val thresholdMillis = config.receiver.thresholdMillis
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
                    val endUptimeMillis = current.uptimeMillis
                    val wallDurationMillis = endUptimeMillis - startUptimeMillis
                    if (wallDurationMillis > thresholdMillis) {
                        val latestActivity = host.getLatestActivity()?.javaClass?.name
                        if (latestActivity.isNullOrEmpty()) return
                        val endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                        val cpuDurationMillis = endThreadTimeMillis - startThreadTimeMillis
                        handler.post(BlockTask(
                            scene = current.scene.name,
                            latestActivity = latestActivity,
                            startMark = startMark,
                            endMark = current.mark,
                            thresholdMillis = thresholdMillis,
                            startUptimeMillis = startUptimeMillis,
                            endUptimeMillis = endUptimeMillis,
                            cpuDurationMillis = cpuDurationMillis,
                            isRecordEnabled = host.isRecordEnabled,
                            metadata = current.metadata.toString(),
                            receiver = config.receiver
                        ))
                    }
                }
            }
        }
    }

    private inner class BlockTask(
        private val scene: String,
        private val latestActivity: String,
        private val startMark: Long,
        private val endMark: Long,
        private val thresholdMillis: Long,
        private val startUptimeMillis: Long,
        private val endUptimeMillis: Long,
        private val cpuDurationMillis: Long,
        private val isRecordEnabled: Boolean,
        private val metadata: String,
        private val receiver: BlockMetricsReceiver
    ) : Runnable {

        override fun run() {
            val createTimeMillis = System.currentTimeMillis()
            val snapshot = host.snapshot(startMark, endMark)
            val sampleList = host.sampleList(startUptimeMillis, endUptimeMillis)
            val procStat = ProcStat.get(Process.myPid())
            receiver.receive(BlockMetrics(
                pid = Process.myPid(),
                tid = Process.myPid(), // 主线程的tid跟pid一致
                scene = scene,
                latestActivity = latestActivity,
                priority = procStat.priority,
                nice = procStat.nice,
                createTimeMillis = createTimeMillis,
                thresholdMillis = thresholdMillis,
                wallDurationMillis = endUptimeMillis - startUptimeMillis,
                cpuDurationMillis = cpuDurationMillis,
                isRecordEnabled = isRecordEnabled,
                metadata = metadata,
                snapshot = snapshot,
                sampleList = sampleList,
            ))
        }
    }
}