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
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.future.Future
import com.xiaocydx.performance.runtime.future.Pending
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.history.segment.Segment
import com.xiaocydx.performance.runtime.history.segment.collectFrom
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class ANRMetricsAnalyzer(
    host: Host,
    private val config: ANRMetricsConfig
) : Analyzer(host) {

    override fun init() {
        coroutineScope.launch {
            host.registerHistory(this@ANRMetricsAnalyzer)
            val callback = Callback(host.merger(
                idleThresholdMillis = config.receiver.idleThresholdMillis,
                mergeThresholdMillis = config.receiver.mergeThresholdMillis
            ))
            host.addCallback(callback)
            try {
                collectANREvent(anrAction = callback::anrAction)
            } finally {
                host.removeCallback(callback)
                host.registerHistory(this@ANRMetricsAnalyzer)
            }
        }
    }

    private companion object {
        const val SYSTEM_DUMP_TIMEOUT_MILLIS = 20 * 1000L
        const val AMS_ERROR_RETRY_COUNT = 20
        const val AMS_ERROR_RETRY_MILLIS = SYSTEM_DUMP_TIMEOUT_MILLIS / AMS_ERROR_RETRY_COUNT
    }

    private suspend fun collectANREvent(anrAction: (Long) -> Unit) {
        val lastANRUptimeMillis = AtomicLong()
        val dumpDispatcher = Handler(host.dumpLooper).asCoroutineDispatcher()
        // Dispatchers.Unconfined:
        // 在emit anrEvent的线程记录anrUptimeMillis和主线程堆栈，让ANRMetrics更为准确
        withContext(Dispatchers.Unconfined) {
            // 产生一次ANR，会按顺序发送多次anrEvent，sysDumpTimeoutMillis期间只处理首次
            host.anrEvent.collect {
                val anrUptimeMillis = SystemClock.uptimeMillis()
                if (anrUptimeMillis - lastANRUptimeMillis.get() < SYSTEM_DUMP_TIMEOUT_MILLIS) return@collect
                lastANRUptimeMillis.set(anrUptimeMillis)
                // TODO: 在此处调用Sampler.sample()抓取主线程堆栈
                // TODO: fast path：判断主线程是否block中
                // slow path：轮询ams，判断是否存在pid的ANR信息
                launch(dumpDispatcher) {
                    val pid = Process.myPid()
                    var processErrorStateInfo: ProcessErrorStateInfo? = null
                    var retry = AMS_ERROR_RETRY_COUNT
                    while (retry > 0) {
                        retry--
                        val processesInErrorState = host.ams.processesInErrorState
                        processErrorStateInfo = processesInErrorState
                            ?.find { it.pid == pid && it.condition == NOT_RESPONDING }
                        if (processErrorStateInfo != null) break
                        delay(AMS_ERROR_RETRY_MILLIS)
                    }
                    if (processErrorStateInfo != null) anrAction(anrUptimeMillis)
                }
            }
        }
    }

    private inner class Callback(private val merger: Merger) : LooperCallback {
        private val segment = Segment()
        private val handler = Handler(Looper.getMainLooper())
        private var anrUptimeMillis = 0L

        @WorkerThread
        fun anrAction(anrUptimeMillis: Long) {
            handler.postAtFrontOfQueue { this.anrUptimeMillis = anrUptimeMillis }
        }

        @MainThread
        override fun dispatch(current: DispatchContext) {
            // collectFrom() time << 1ms
            segment.collectFrom(current)
            if (current !is End) return
            if (anrUptimeMillis == 0L) {
                merger.consume(segment)
            } else {
                val anrTime = anrUptimeMillis
                anrUptimeMillis = 0L
                segment.reset()
                val endUptimeMillis = current.uptimeMillis
                val startUptimeMillis = endUptimeMillis - (15 * 1000)
                val elements = merger.copy(startUptimeMillis, endUptimeMillis)
                if (elements.isEmpty()) return
                val future = Future.getPendingList(Looper.myQueue(), endUptimeMillis)
            }
        }
    }

    private class ANRTask(
        private val elements: List<Any>,
        private val future: List<Pending>
    ) : Runnable {

        override fun run() {
            // TODO: 将Element转换为Group
        }
    }
}