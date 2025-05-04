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

package com.xiaocydx.performance.plugin.generate.anr

import com.xiaocydx.performance.plugin.generate.GenerateContext
import com.xiaocydx.performance.plugin.generate.MetricsParser
import com.xiaocydx.performance.plugin.generate.Record
import com.xiaocydx.performance.plugin.generate.Sample
import com.xiaocydx.performance.plugin.generate.TraceEvent
import java.io.File
import kotlin.math.max

/**
 * @author xcc
 * @date 2025/4/30
 */
internal class ANRMetricsParser : MetricsParser<ANRMetrics> {

    override fun match(tag: String): Class<ANRMetrics>? {
        return if (tag == ANRMetrics.TAG) ANRMetrics::class.java else null
    }

    override fun json(file: File, metrics: ANRMetrics, context: GenerateContext): String? {
        val outcome = mutableListOf<TraceEvent>()
        if (!addHistoryEvents(file, metrics, context, outcome)) return null
        addFutureEvents(metrics, outcome)
        outcome.add(TraceEvent.instant(
            name = "ANRSample",
            ts = TraceEvent.ts(metrics.anrSample.uptimeMillis),
            pid = TraceEvent.pid(metrics.pid),
            tid = TraceEvent.tid(metrics.tid),
            args = metrics.anrSample
        ))
        outcome.sortBy { it.ts }
        return context.gson.toJson(outcome)
    }

    private fun addHistoryEvents(
        file: File,
        metrics: ANRMetrics,
        context: GenerateContext,
        outcome: MutableList<TraceEvent>
    ): Boolean {
        // gson构建的batch，wallDurationMillis和cpuDurationMillis为0，通过copy()赋值
        val history = metrics.history.map { it.copy() }
        history.forEachIndexed { i: Int, batch ->
            var cpu = ""
            if (batch.cpuDurationMillis >= 0) {
                cpu = ", cpu=${batch.cpuDurationMillis}ms"
            }
            val batchName = "Batch#${i + 1} { count=${batch.count}, " +
                    "scene=${batch.scene}, wall=${batch.wallDurationMillis}ms${cpu} }"
            outcome.add(batchTraceEvent(batchName, isBegin = true, batch, metrics))

            batch.sampleList.mapIndexed { i: Int, sample: Sample ->
                outcome.add(TraceEvent.instant(
                    name = "BatchSample${i + 1}",
                    ts = TraceEvent.ts(sample.uptimeMillis),
                    pid = TraceEvent.pid(metrics.pid),
                    tid = TraceEvent.tid(metrics.tid),
                    args = sample
                ))
            }

            filter(batch.snapshot).forEach {
                val record = Record(it)
                val methodData = context.mappingMethod[record.id]
                if (methodData == null) {
                    context.logger.lifecycle { "${file.name} [failure]: id = ${record.id} not exists" }
                    return false
                }
                val className = methodData.className.replace("/", ".")
                outcome.add(TraceEvent.duration(
                    name = "${className}.${methodData.methodName}",
                    isBegin = record.isEnter,
                    ts = TraceEvent.ts(record.timeMs),
                    pid = TraceEvent.pid(metrics.pid),
                    tid = "Snapshot",
                    cat = batch.scene
                ))
            }

            outcome.add(batchTraceEvent(batchName, isBegin = false, batch, metrics))
        }
        return true
    }

    private fun batchTraceEvent(
        name: String,
        isBegin: Boolean,
        batch: CompletedBatch,
        metrics: ANRMetrics,
    ) = TraceEvent.duration(
        name = name,
        isBegin = isBegin,
        ts = TraceEvent.ts(if (isBegin) batch.startUptimeMillis else batch.endUptimeMillis),
        pid = TraceEvent.pid(metrics.pid),
        tid = TraceEvent.tid(metrics.tid),
        cat = batch.scene,
        // 去除snapshot和sampleList，避免查看TraceEvent出现卡顿
        args = if (isBegin) batch.copy(snapshot = emptyList(), sampleList = emptyList()) else null
    )

    private fun addFutureEvents(metrics: ANRMetrics, outcome: MutableList<TraceEvent>) {
        val futureIntervalTs = TraceEvent.ts(FUTURE_INTERVAL_MS)
        val lastTs = outcome.lastOrNull()?.ts ?: 0L
        val futureBaseTs = max(lastTs, TraceEvent.ts(metrics.anrSample.uptimeMillis))
        metrics.future.forEachIndexed { i, pending ->
            val startTs = futureBaseTs + i * futureIntervalTs
            val endTs = startTs + futureIntervalTs
            outcome.add(TraceEvent.complete(
                name = " Pending#${i + 1}${pending}",
                startTs = startTs,
                endTs = endTs,
                pid = TraceEvent.pid(metrics.pid),
                tid = TraceEvent.tid(metrics.tid),
                args = pending
            ))
        }

        val paddingIntervalTs = TraceEvent.ts(PADDING_INTERVAL_MS)
        val paddingStartTs = futureBaseTs + metrics.future.size * futureIntervalTs
        outcome.add(TraceEvent.complete(
            name = " ",
            startTs = paddingStartTs,
            endTs = paddingStartTs + paddingIntervalTs,
            pid = TraceEvent.pid(metrics.pid),
            tid = TraceEvent.tid(metrics.tid)
        ))
    }

    private companion object {
        const val FUTURE_INTERVAL_MS = 1000L
        const val PADDING_INTERVAL_MS = 3000L
    }
}

internal data class ANRMetrics(
    val tag: String = "",
    val pid: Int = 0,
    val tid: Int = 0,
    val createTimeMillis: Long = 0L,
    val latestActivity: String = "",
    val thresholdMillis: Long = 0L,
    val isRecordEnabled: Boolean = false,
    val anrSample: Sample = Sample(),
    val history: List<CompletedBatch> = emptyList(),
    val future: List<PendingMessage> = emptyList()
) {
    companion object {
        const val TAG = "ANRMetrics"
    }
}