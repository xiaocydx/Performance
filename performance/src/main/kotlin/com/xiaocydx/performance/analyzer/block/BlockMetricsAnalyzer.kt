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
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.ProcStat
import com.xiaocydx.performance.runtime.history.History
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
    host: Performance.Host,
    private val config: BlockMetricsConfig
) : Analyzer(host) {

    override fun init() {
        val handler = Handler(host.dumpLooper)
        val callback = Callback(handler)
        coroutineScope.launch {
            host.needHistory(this@BlockMetricsAnalyzer)
            host.addCallback(callback)
            awaitCancellation()
        }.invokeOnCompletion {
            host.removeCallback(callback)
        }
    }

    private inner class Callback(private val handler: Handler) : Runnable, LooperCallback {
        private var startMark = 0L
        private var startUptimeMillis = 0L
        private var startThreadTimeMillis = 0L
        @Volatile private var sampleState: String? = null
        @Volatile private var sampleStack: Array<StackTraceElement>? = null

        override fun run() {
            val thread = Looper.getMainLooper().thread
            sampleState = thread.state.name
            sampleStack = thread.stackTrace
        }

        private fun consumeSampleState(): String? {
            val sampleState = sampleState ?: return null
            this.sampleState = null
            return sampleState
        }

        private fun consumeSampleStack(): Array<StackTraceElement>? {
            val sampleStack = sampleStack ?: return null
            this.sampleStack = null
            return sampleStack
        }

        override fun dispatch(current: DispatchContext) {
            val thresholdMillis = config.receiver.thresholdMillis
            when (current) {
                is Start -> {
                    handler.postDelayed(this, (thresholdMillis * 0.7).toLong())
                    startMark = History.startMark()
                    startUptimeMillis = current.uptimeMillis
                    startThreadTimeMillis = current.threadTimeMillis
                }
                is End -> {
                    handler.removeCallbacks(this)
                    val endMark = History.endMark()
                    val wallDurationMillis = current.uptimeMillis - startUptimeMillis
                    if (wallDurationMillis > thresholdMillis) {
                        val endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                        val cpuDurationMillis = endThreadTimeMillis - startThreadTimeMillis
                        handler.post(BlockTask(
                            scene = current.scene.name,
                            lastActivity = host.getLastActivity()?.javaClass?.name ?: "",
                            startMark = startMark,
                            endMark = endMark,
                            thresholdMillis = thresholdMillis,
                            wallDurationMillis = wallDurationMillis,
                            cpuDurationMillis = cpuDurationMillis,
                            isRecordEnabled = History.isRecordEnabled,
                            metadata = current.metadata.toString(),
                            sampleState = consumeSampleState(),
                            sampleStack = consumeSampleStack(),
                            receiver = config.receiver
                        ))
                    }
                }
            }
        }
    }

    private class BlockTask(
        private val scene: String,
        private val lastActivity: String,
        private val startMark: Long,
        private val endMark: Long,
        private val thresholdMillis: Long,
        private val wallDurationMillis: Long,
        private val cpuDurationMillis: Long,
        private val isRecordEnabled: Boolean,
        private val metadata: String,
        private val sampleState: String?,
        private val sampleStack: Array<StackTraceElement>?,
        private val receiver: BlockMetricsReceiver
    ) : Runnable {

        override fun run() {
            val snapshot = History.snapshot(startMark, endMark)
            val procStat = ProcStat.get(Process.myPid())
            SystemClock.uptimeMillis()
            receiver.receive(BlockMetrics(
                pid = Process.myPid(),
                tid = Process.myPid(), // 主线程的tid跟pid一致
                scene = scene,
                lastActivity = lastActivity,
                priority = procStat.priority,
                nice = procStat.nice,
                createTimeMillis = System.currentTimeMillis(),
                thresholdMillis = thresholdMillis,
                wallDurationMillis = wallDurationMillis,
                cpuDurationMillis = cpuDurationMillis,
                isRecordEnabled = isRecordEnabled,
                metadata = metadata,
                snapshot = snapshot,
                sampleState = sampleState,
                sampleStack = sampleStack
            ))
        }
    }
}