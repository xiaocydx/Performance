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
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.runtime.future.Future
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.history.segment.Merger.Element
import com.xiaocydx.performance.runtime.history.segment.Segment
import com.xiaocydx.performance.runtime.history.segment.collectFrom
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
        coroutineScope.launch {
            host.registerHistory(this@ANRMetricsAnalyzer)
            val callback = Callback(host.merger(
                idleThresholdMillis = anrConfig.idleThresholdMillis,
                mergeThresholdMillis = anrConfig.mergeThresholdMillis
            ))
            host.addCallback(callback)
            try {
                collectANREvent(Looper.myQueue(), callback::anrAction)
            } finally {
                host.removeCallback(callback)
                host.registerHistory(this@ANRMetricsAnalyzer)
            }
        }
    }

    private suspend fun collectANREvent(
        mainQueue: MessageQueue,
        anrAction: (anrSample: Sample) -> Unit
    ) {
        val lastANRTime = AtomicLong()
        val lastANRSample = AtomicReference<Sample>()
        val dumpDispatcher = Handler(host.dumpLooper).asCoroutineDispatcher()
        // Dispatchers.Unconfined:
        // 在发送anrEvent的线程记录anrTime和anrSample，让ANRMetrics更为准确
        withContext(Dispatchers.Unconfined) {
            // 发生ANR会发送多次anrEvent，DUMP_TIMEOUT_MILLIS期间只处理首次
            host.anrEvent.collect {
                val anrTime = SystemClock.uptimeMillis()
                val anrSample = requireNotNull(host.sampleImmediately())
                lastANRSample.set(anrSample)

                if (anrTime - lastANRTime.get() < DUMP_TIMEOUT_MILLIS) return@collect
                lastANRTime.set(anrTime)

                if (isBackground()) {
                    // 可能是后台ANR
                    anrAction(anrSample)
                    return@collect
                }

                // fast path：判断首个消息，处理前台ANR闪退
                if (isPostpone(mainQueue)) {
                    anrAction(anrSample)
                    return@collect
                }

                // slow path：轮询ams，判断是否为前台ANR
                launch(dumpDispatcher) {
                    var processErrorStateInfo: ProcessErrorStateInfo? = null
                    var retry = AMS_ERROR_RETRY_COUNT
                    while (retry > 0) {
                        retry--
                        val processesInErrorState = host.ams.processesInErrorState
                        processErrorStateInfo = processesInErrorState
                            ?.find { it.pid == host.pid && it.condition == NOT_RESPONDING }
                        if (processErrorStateInfo != null) break
                        delay(AMS_ERROR_RETRY_MILLIS)
                    }
                    if (processErrorStateInfo != null) anrAction(lastANRSample.get())
                }
            }
        }
    }

    private fun isBackground(): Boolean {
        if (host.getActiveActivityCount() == 0) return true
        // activeActivityCount > 0进程不一定处于前台，
        // 可能是主线程阻塞中，还没有处理生命周期消息，
        // 获取runningAppProcesses做进一步判断。
        val runningAppProcesses = host.ams.runningAppProcesses ?: emptyList()
        val info = runningAppProcesses.firstOrNull { it.pid == host.pid }
        return info == null || info.importance != IMPORTANCE_FOREGROUND
    }

    private fun isPostpone(mainQueue: MessageQueue): Boolean {
        val first = Future.getFirstPending(mainQueue) ?: return false
        // first.`when` = 0L是Handler.sendMessageAtFrontOfQueue()发送的消息
        return first.`when` != 0L && first.uptimeMillis - first.`when` > POSTPONE_THRESHOLD
    }

    private inner class Callback(private val merger: Merger) : LooperCallback {
        private val segment = Segment()
        private val dumpHandler = Handler(host.dumpLooper)
        private val mainHandler = Handler(Looper.getMainLooper())
        private var anrSample: Sample? = null

        @WorkerThread
        fun anrAction(anrSample: Sample) {
            mainHandler.postAtFrontOfQueue { this.anrSample = anrSample }
        }

        @MainThread
        override fun dispatch(current: DispatchContext) {
            // collectFrom() time << 1ms
            segment.collectFrom(current)
            when {
                current !is End -> return
                anrSample == null -> {
                    if (segment.wallDurationMillis > blockConfig.thresholdMillis) {
                        segment.isSingle = true
                        segment.needRecord = true
                        segment.needSample = true
                        segment.endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                    } else if (current.isFrom(Source.ActivityThread)) {
                        segment.isSingle = true
                        segment.endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                    }
                    merger.consume(segment)
                }
                else -> {
                    segment.reset()
                    val anrSample = anrSample
                    this.anrSample = null
                    val startUptimeMillis = current.uptimeMillis - anrConfig.recentDurationMillis
                    val elements = merger.copy(startUptimeMillis, current.uptimeMillis)
                    val future = Future.getPendingList(Looper.myQueue(), current.uptimeMillis)
                    dumpHandler.post(ANRTask(
                        elements = elements,
                        intermediate = ANRMetrics(
                            //region lack
                            pid = 0,
                            tid = 0,
                            createTimeMillis = 0L,
                            history = emptyList(),
                            //endregion
                            latestActivity = host.getLatestActivity()?.javaClass?.name ?: "",
                            thresholdMillis = blockConfig.thresholdMillis,
                            isRecordEnabled = host.isRecordEnabled,
                            anrSample = anrSample!!,
                            future = future
                        )
                    ))
                }
            }
        }
    }

    private inner class ANRTask(
        private val elements: List<Element>,
        private val intermediate: ANRMetrics
    ) : Runnable {

        override fun run() {
            val createTimeMillis = System.currentTimeMillis()
            val anrSample = intermediate.anrSample
            val history = arrayOfNulls<CompletedBatch>(elements.size)
            for (i in elements.lastIndex downTo 0) {
                val element = elements[i]
                val last = element.last
                val lastStartTime = last.startUptimeMillis
                val lastEndTime = last.endUptimeMillis
                val containsANR = anrSample.uptimeMillis in lastStartTime..lastEndTime

                var snapshot = Snapshot.empty()
                var sampleList = emptyList<Sample>()
                if (containsANR || element.needRecord) {
                    snapshot = host.snapshot(last.startMark, last.endMark).available(lastEndTime)
                }
                if (containsANR || element.needSample) {
                    sampleList = host.sampleList(lastStartTime, lastEndTime)
                }

                history[i] = CompletedBatch(
                    count = element.count,
                    scene = element.scene.toString(),
                    lastMetadata = element.lastMetadata(),
                    startUptimeMillis = element.startUptimeMillis,
                    startThreadTimeMillis = element.startThreadTimeMillis,
                    endUptimeMillis = element.endUptimeMillis,
                    endThreadTimeMillis = element.endThreadTimeMillis,
                    idleDurationMillis = element.idleDurationMillis,
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