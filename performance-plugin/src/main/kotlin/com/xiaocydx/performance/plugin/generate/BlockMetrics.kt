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

package com.xiaocydx.performance.plugin.generate

import com.xiaocydx.performance.plugin.generate.Record.Companion.ID_SLICE
import com.xiaocydx.performance.plugin.metadata.MethodData
import java.io.File

/**
 * @author xcc
 * @date 2025/4/24
 */
internal data class BlockMetrics(
    val pid: Int = 0,
    val tid: Int = 0,
    val scene: String = "",
    val latestActivity: String = "",
    val priority: Int = 0,
    val nice: Int = 0,
    val createTimeMillis: Long = 0L,
    val thresholdMillis: Long = 0L,
    val wallDurationMillis: Long = 0L,
    val cpuDurationMillis: Long = 0L,
    val isRecordEnabled: Boolean = false,
    val metadata: String = "",
    val snapshot: List<Long> = emptyList(),
    val sampleData: SampleData = SampleData()
) {
    companion object {
        const val TAG = "BlockMetrics"
    }
}

internal class BlockMetricsParser : MetricsParser<BlockMetrics> {

    override fun matchDataClass(tag: String): Class<BlockMetrics>? {
        return if (tag == BlockMetrics.TAG) BlockMetrics::class.java else null
    }

    override fun toTraceEventsJson(
        file: File,
        data: BlockMetrics,
        context: GenerateContext
    ): String? = with(data) {
        if (snapshot.isEmpty()) {
            context.logger.lifecycle { "${file.name} [failure]: snapshot is empty" }
            return null
        }
        val durationEvents = snapshot.map {
            val record = Record(it)
            var methodData = context.mappingMethod[record.id]
            if (methodData == null) {
                if (record.id != ID_SLICE) {
                    context.logger.lifecycle { "${file.name} [failure]: id = ${record.id} not exists" }
                    return null
                }
                methodData = MethodData(
                    id = ID_SLICE, access = 0,
                    className = BlockMetrics.TAG,
                    methodName = scene,
                    desc = ""
                )
            }
            val className = methodData.className.replace("/", ".")
            TraceEvent.duration(
                name = "${className}.${methodData.methodName}",
                isBegin = record.isEnter,
                ts = TraceEvent.ts(record.timeMs),
                pid = TraceEvent.pid(pid),
                tid = TraceEvent.tid(tid),
                cat = scene,
                args = if (record.id == ID_SLICE) this.copy(snapshot = emptyList()) else null
            )
        }

        val instantEvent = TraceEvent.instant(
            name = "SampleData",
            ts = TraceEvent.ts(sampleData.uptimeMillis),
            pid = TraceEvent.pid(pid),
            tid = "SampleData",
            args = sampleData
        )
        return context.gson.toJson(durationEvents + instantEvent)
    }
}