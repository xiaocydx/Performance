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

package com.xiaocydx.performance.plugin.generate.block

import com.xiaocydx.performance.plugin.generate.GenerateContext
import com.xiaocydx.performance.plugin.generate.MetricsParser
import com.xiaocydx.performance.plugin.generate.Record
import com.xiaocydx.performance.plugin.generate.Sample
import com.xiaocydx.performance.plugin.generate.TraceEvent
import java.io.File

/**
 * @author xcc
 * @date 2025/4/24
 */
internal data class BlockMetrics(
    val tag: String = "",
    val pid: Int = 0,
    val tid: Int = 0,
    val createTimeMillis: Long = 0L,
    val scene: String = "",
    val metadata: String = "",
    val latestActivity: String = "",
    val thresholdMillis: Long = 0L,
    val startUptimeMillis: Long = 0L,
    val startThreadTimeMillis: Long = 0L,
    val endUptimeMillis: Long = 0L,
    val endThreadTimeMillis: Long = 0L,
    val isRecordEnabled: Boolean = false,
    val snapshot: List<Long> = emptyList(),
    val sampleList: List<Sample> = emptyList(),
) {
    val wallDurationMillis = endUptimeMillis - startUptimeMillis
    val cpuDurationMillis = endThreadTimeMillis - startThreadTimeMillis

    companion object {
        const val TAG = "BlockMetrics"
    }
}

internal class BlockMetricsParser : MetricsParser<BlockMetrics> {

    override fun match(tag: String): Class<BlockMetrics>? {
        return if (tag == BlockMetrics.TAG) BlockMetrics::class.java else null
    }

    override fun json(
        file: File,
        metrics: BlockMetrics,
        context: GenerateContext
    ): String? = with(metrics) {
        val snapshotEvents = filter(snapshot).map {
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
                tid = TraceEvent.tid(tid),
                cat = scene
            )
        }.toMutableList()

        // gson构建的metrics，wallDurationMillis和cpuDurationMillis为0，通过copy()赋值
        val args = metrics.copy()
        repeat(2) {
            val isEnter = it == 0
            val index = if (isEnter) 0 else snapshotEvents.size
            val timeMs = if (isEnter) startUptimeMillis else endUptimeMillis
            snapshotEvents.add(index, TraceEvent.duration(
                name = "${BlockMetrics.TAG}.${scene}",
                isBegin = isEnter,
                ts = TraceEvent.ts(timeMs),
                pid = TraceEvent.pid(pid),
                tid = TraceEvent.tid(tid),
                cat = scene,
                args = if (isEnter) args else null
            ))
        }

        val sampleEvents = sampleList.mapIndexed { i: Int, sample: Sample ->
            TraceEvent.instant(
                name = "Sample${i + 1}",
                ts = TraceEvent.ts(sample.uptimeMillis),
                pid = TraceEvent.pid(pid),
                tid = "Sample",
                args = sample
            )
        }
        return context.gson.toJson(snapshotEvents + sampleEvents)
    }
}