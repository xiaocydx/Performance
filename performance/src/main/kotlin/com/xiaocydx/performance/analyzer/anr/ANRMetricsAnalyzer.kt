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

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.history.segment.Merger.Element
import com.xiaocydx.performance.runtime.history.segment.Segment
import com.xiaocydx.performance.runtime.history.segment.collectFrom
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

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
            val watchDog = ANRWatchDog(host.ams)
            host.addCallback(callback)
            host.addCallback(watchDog)
            watchDog.start(anrAction = callback::anrAction)
            try {
                awaitCancellation()
            } finally {
                watchDog.stop()
                host.removeCallback(watchDog)
                host.removeCallback(callback)
            }
        }
    }

    private inner class Callback(private val merger: Merger) : LooperCallback {
        private val segment = Segment()
        private val handler = Handler(Looper.getMainLooper())
        private var isANRMessage = false

        @WorkerThread
        fun anrAction() {
            handler.postAtFrontOfQueue { isANRMessage = true }
        }

        @MainThread
        override fun dispatch(current: DispatchContext) {
            // collectFrom() time << 1ms
            segment.collectFrom(current)
            if (current !is End) return
            if (!isANRMessage) {
                merger.consume(segment)
            } else {
                isANRMessage = false
                segment.reset()
                val endUptimeMillis = current.uptimeMillis
                val startUptimeMillis = endUptimeMillis - (15 * 1000)
                var elements = merger.peek(startUptimeMillis, endUptimeMillis)
                if (elements.isEmpty()) return

                val last = elements.last()
                elements = elements - last
                val history = elements.map { it.toGroup() }
                val current = last.toGroup()
            }
        }
    }

    private fun Element.toGroup() = Group(
        count = count,
        scene = scene.name,
        startUptimeMillis = startUptimeMillis,
        endUptimeMillis = endUptimeMillis,
        cpuDurationMillis = 0L,// TODO: 补充
        metadata = "",// TODO: 补充
        snapshot = host.snapshot(last.startMark, last.endMark),// TODO: 补充判断条件
        sampleList = host.sampleList(last.startUptimeMillis, last.endUptimeMillis)// TODO: 补充判断条件
    )

    private data class Temp(
        val pid: Int,
        val tid: Int,
        val latestActivity: String,
        val createTimeMillis: Long,
        val history: List<Group>,
        val current: Group,
        val future: List<Any>
    )

    private data class Group(
        val count: Int,
        val scene: String,
        val startUptimeMillis: Long,
        val endUptimeMillis: Long,
        val cpuDurationMillis: Long,
        val metadata: String,
        val snapshot: Snapshot,
        val sampleList: List<Sample>
    )
}