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

import android.app.ActivityManager.ProcessErrorStateInfo
import android.app.ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.xiaocydx.performance.HistoryToken
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.runtime.future.PendingMessage
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.segment.Merger.Range
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class ANRMetricsAnalyzer(
    host: Host,
    private val anrConfig: ANRMetricsConfig,
    private val blockConfig: BlockMetricsConfig
) : Analyzer(host) {

    override fun init() {
        val token = HistoryToken(
            analyzer = this,
            needSignal = true,
            needSample = true,
            needSegment = true
        )
        val collector = ANREventCollector()
        coroutineScope.launch {
            host.registerHistory(token)
            collector.collectANREvent()
        }.invokeOnCompletion {
            host.registerHistory(token)
        }
    }

    private inner class ANREventCollector {
        private val dumpHandler = Handler(host.dumpLooper)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val dumpDispatcher = dumpHandler.asCoroutineDispatcher()

        suspend fun collectANREvent(): Unit = withContext(dumpDispatcher) {
            var lastANRTime = 0L
            var lastANRSample: Sample
            var lastFuture: List<PendingMessage>
            host.anrEvent.collect {
                val anrTime = SystemClock.uptimeMillis()
                lastANRSample = requireNotNull(host.sampleImmediately())
                lastFuture = host.getPendingList(anrTime)

                if (anrTime - lastANRTime < DUMP_TIMEOUT_MILLIS) {
                    // ANR会发送多次event，DUMP_TIMEOUT_MILLIS期间只处理首次
                    return@collect
                }
                lastANRTime = anrTime

                val runningAppProcesses = host.ams.runningAppProcesses ?: emptyList()
                val processInfo = runningAppProcesses.firstOrNull { it.pid == host.pid }

                // processInfo == null可能是前台ANR闪退或后台ANR，这两种情况会杀掉进程
                if (processInfo == null || isBackground(processInfo)) {
                    proceed(lastANRTime, lastANRSample, lastFuture, immediately = processInfo == null)
                    return@collect
                }

                // fast path：判断首个消息，处理前台ANR
                if (isPostpone(lastFuture)) {
                    proceed(lastANRTime, lastANRSample, lastFuture, immediately = false)
                    return@collect
                }

                // slow path：轮询ams，判断是否为前台ANR
                launch(start = CoroutineStart.UNDISPATCHED) {
                    var retry = AMS_ERROR_RETRY_COUNT
                    var processErrorStateInfo: ProcessErrorStateInfo? = null
                    while (retry > 0) {
                        retry--
                        processErrorStateInfo = host.ams.processesInErrorState
                            ?.find { it.pid == host.pid && it.condition == NOT_RESPONDING }
                        if (processErrorStateInfo != null) break
                        delay(AMS_ERROR_RETRY_MILLIS)
                    }
                    if (processErrorStateInfo != null) {
                        proceed(lastANRTime, lastANRSample, lastFuture, immediately = false)
                    }
                }
            }
        }

        private fun isBackground(processInfo: RunningAppProcessInfo): Boolean {
            if (host.getActiveActivityCount() == 0) return true
            // activeActivityCount > 0进程不一定处于前台，
            // 可能是主线程阻塞中，还没有处理生命周期消息，
            // 通过processInfo做进一步判断。
            return processInfo.importance != IMPORTANCE_FOREGROUND
        }

        private fun isPostpone(future: List<PendingMessage>): Boolean {
            val first = future.firstOrNull() ?: return false
            // first.`when` = 0L是Handler.sendMessageAtFrontOfQueue()发送的消息
            return first.`when` != 0L && first.uptimeMillis - first.`when` > POSTPONE_THRESHOLD
        }

        private fun proceed(
            anrTime: Long,
            anrSample: Sample,
            future: List<PendingMessage>,
            immediately: Boolean
        ) {
            // TODO: immediately = true：不做调度、合并当前Segment、获取堆栈和快照
            mainHandler.postAtFrontOfQueue {
                val startUptimeMillis = anrTime - anrConfig.recentDurationMillis
                val ranges = host.segmentRange(startUptimeMillis, endUptimeMillis = anrTime)
                dumpHandler.post(ANRMetricsTask(
                    ranges = ranges,
                    intermediate = ANRMetrics(
                        //region lack
                        pid = 0,
                        tid = 0,
                        createTimeMillis = 0L,
                        history = emptyList(),
                        //endregion
                        latestActivity = host.getLatestActivity()?.javaClass?.name ?: "",
                        blockThresholdMillis = blockConfig.blockThresholdMillis,
                        sampleIntervalMillis = blockConfig.sampleIntervalMillis,
                        isRecordEnabled = host.isRecordEnabled,
                        anrSample = anrSample,
                        future = future
                    )
                ))
            }
        }
    }

    private inner class ANRMetricsTask(
        private val ranges: List<Range>,
        private val intermediate: ANRMetrics
    ) : Runnable {

        override fun run() {
            val createTimeMillis = System.currentTimeMillis()
            val anrSample = intermediate.anrSample
            val history = arrayOfNulls<CompletedBatch>(ranges.size)
            for (i in ranges.lastIndex downTo 0) {
                val range = ranges[i]
                val last = range.last
                val lastStartTime = last.startUptimeMillis
                val lastEndTime = last.endUptimeMillis
                val containsANR = anrSample.uptimeMillis in lastStartTime..lastEndTime

                var snapshot = Snapshot.empty()
                var sampleList = emptyList<Sample>()
                if (containsANR || range.needRecord) {
                    snapshot = host.snapshot(last.startMark, last.endMark).available(lastEndTime)
                }
                if (containsANR || range.needSample) {
                    sampleList = host.sampleList(lastStartTime, lastEndTime)
                }

                history[i] = CompletedBatch(
                    count = range.count,
                    scene = range.scene.toString(),
                    lastMetadata = range.lastMetadata(),
                    startUptimeMillis = range.startUptimeMillis,
                    startThreadTimeMillis = range.startThreadTimeMillis,
                    endUptimeMillis = range.endUptimeMillis,
                    endThreadTimeMillis = range.endThreadTimeMillis,
                    idleDurationMillis = range.idleDurationMillis,
                    snapshot = snapshot,
                    sampleList = sampleList,
                )
            }

            @Suppress("UNCHECKED_CAST")
            val metric = intermediate.copy(
                pid = host.pid,
                tid = host.pid, // 主线程的tid跟pid一致
                createTimeMillis = createTimeMillis,
                history = history.asList() as List<CompletedBatch>
            )
            anrConfig.receivers.forEach { it.receive(metric) }
        }
    }

    private companion object {
        const val DUMP_TIMEOUT_MILLIS = 20 * 1000L
        const val AMS_ERROR_RETRY_COUNT = 20
        const val AMS_ERROR_RETRY_MILLIS = DUMP_TIMEOUT_MILLIS / AMS_ERROR_RETRY_COUNT
        const val POSTPONE_THRESHOLD = 2000L
    }
}