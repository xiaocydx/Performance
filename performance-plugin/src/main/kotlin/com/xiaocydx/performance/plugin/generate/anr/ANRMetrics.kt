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

/**
 * @author xcc
 * @date 2025/4/30
 */
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

internal class ANRMetricsParser : MetricsParser<ANRMetrics> {

    override fun match(tag: String): Class<ANRMetrics>? {
        return if (tag == ANRMetrics.TAG) ANRMetrics::class.java else null
    }

    override fun json(
        file: File,
        metrics: ANRMetrics,
        context: GenerateContext
    ): String? = with(metrics) {
        // gson构建的batch，wallDurationMillis和cpuDurationMillis为0，通过copy()赋值
        val history = history.map { it.copy() }
        val historyEvents = history.mapIndexed { i: Int, batch ->
            TraceEvent.complete(
                name = " Batch#${i + 1} { count=${batch.count}, scene=${batch.scene}, " +
                        "wall=${batch.wallDurationMillis}ms, cpu=${batch.cpuDurationMillis}ms }",
                startTs = TraceEvent.ts(batch.startUptimeMillis),
                endTs = TraceEvent.ts(batch.endUptimeMillis),
                pid = TraceEvent.pid(pid),
                tid = TraceEvent.tid(tid),
                cat = batch.scene,
                args = batch
            )
        }

        val intervalMs = 10
        val futureEvents = future.mapIndexed { i, pending ->
            val startMs = pending.uptimeMillis + i * intervalMs
            val endMs = startMs + intervalMs
            TraceEvent.complete(
                name = " Pending#${i + 1}",
                startTs = TraceEvent.ts(startMs),
                endTs = TraceEvent.ts(endMs),
                pid = TraceEvent.pid(pid),
                tid = TraceEvent.tid(tid),
                args = pending
            )
        }

        val snapshotEvents = history.flatMap { batch ->
            filter(batch.snapshot).map {
                val record = Record(it)
                val methodData = context.mappingMethod[record.id]
                if (methodData == null) {
                    context.logger.lifecycle { "${file.name} [failure]: id = ${record.id} not exists" }
                    return null
                }
                val className = methodData.className.replace("/", ".")
                TraceEvent.duration(
                    name = "${className}.${methodData.methodName}",
                    isBegin = record.isEnter,
                    ts = TraceEvent.ts(record.timeMs),
                    pid = TraceEvent.pid(pid),
                    tid = "BatchSnapshot",
                    cat = batch.scene
                )
            }
        }

        val sampleEvents = history.flatMap { batch ->
            batch.sampleList.mapIndexed { i: Int, sample: Sample ->
                TraceEvent.instant(
                    name = "BatchSample${i + 1}",
                    ts = TraceEvent.ts(sample.uptimeMillis),
                    pid = TraceEvent.pid(pid),
                    tid = "BatchSample",
                    args = sample
                )
            }
        }.toMutableList()

        sampleEvents.add(TraceEvent.instant(
            name = "ANRSample",
            ts = TraceEvent.ts(anrSample.uptimeMillis),
            pid = TraceEvent.pid(pid),
            tid = "ANRSample",
            args = anrSample
        ))
        return context.gson.toJson(historyEvents + futureEvents + snapshotEvents + sampleEvents)
    }
}