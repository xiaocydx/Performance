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

package com.xiaocydx.performance.plugin.task

import com.google.gson.Gson
import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.PerformanceExtension
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import com.xiaocydx.performance.plugin.task.GenerateJsonTask.Record.Companion.ID_SLICE
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author xcc
 * @date 2025/4/18
 */
internal abstract class GenerateJsonTask : DefaultTask() {
    private val logger = Logger(javaClass)

    @TaskAction
    fun taskAction() {
        val ext = PerformanceExtension.getHistory(project)
        val mappingMethodFile = File(ext.mappingMethodFile)
        val mappingSnapshotDir = File(ext.mappingSnapshotDir)
        require(mappingMethodFile.exists()) { "$mappingMethodFile not exists" }
        require(mappingSnapshotDir.exists()) { "$mappingSnapshotDir not exists" }

        val gson = Gson()
        val mapping = mappingMethodFile.bufferedReader(Metadata.charset).useLines { lines ->
            lines.map { MethodData.fromOutput(it) }.associateBy { it.id }
        }
        val listFiles = mappingSnapshotDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val metricsFiles = listFiles.filter { it.name.startsWith("BlockMetrics") }
        val metricsList = metricsFiles.map { gson.fromJson(it.readText(), BlockMetrics::class.java) }

        val jsonDir = File(mappingSnapshotDir, "json")
        jsonDir.mkdirs()
        metricsList.forEachIndexed { i, metrics ->
            val metricsFile = metricsFiles[i]
            if (metrics.snapshot.isEmpty()) {
                logger.lifecycle { "${metricsFile.name} [failure]: snapshot is empty" }
                return@forEachIndexed
            }

            val args = metrics.copy(snapshot = emptyList())
            val events = metrics.snapshot.map {
                val record = Record(it)
                var methodData = mapping[record.id]
                if (methodData == null) {
                    require(record.id == ID_SLICE) { "id = ${record.id}" }
                    methodData = MethodData(
                        id = ID_SLICE, access = 0,
                        className = "BlockMetrics",
                        methodName = metrics.scene,
                        desc = ""
                    )
                }
                TraceEvent(
                    name = "${methodData.className}.${methodData.methodName}",
                    ph = if (record.isEnter) TraceEvent.B else TraceEvent.E,
                    ts = record.timeMs * 1000, // to microsecond
                    pid = metrics.pid.toLong(),
                    tid = metrics.tid.toLong(),
                    cat = metrics.scene,
                    args = if (record.id == ID_SLICE) args else null
                )
            }
            val file = File(jsonDir, "${metricsFiles[i].name}.json")
            file.bufferedWriter().use { it.write(gson.toJson(events)) }
            logger.lifecycle { "${metricsFile.name} [success]: ${file.absolutePath}" }
        }
    }

    private data class BlockMetrics(
        val pid: Int = 0,
        val tid: Int = 0,
        val scene: String = "",
        val lastActivity: String = "",
        val priority: Int = 0,
        val nice: Int = 0,
        val createTimeMillis: Long = 0L,
        val thresholdMillis: Long = 0L,
        val wallDurationMillis: Long = 0L,
        val cpuDurationMillis: Long = 0L,
        val isRecordEnabled: Boolean = false,
        val metadata: String = "",
        val snapshot: List<Long> = emptyList(),
        val stackTrace: List<String> = emptyList()
    )

    @JvmInline
    private value class Record(val value: Long) {

        inline val id: Int
            get() = ((value ushr SHL_BITS_ID) and MASK_ID).toInt()

        inline val timeMs: Long
            get() = value and MASK_TIME_MS

        inline val isEnter: Boolean
            get() = (value ushr SHL_BITS_ENTER) == 1L

        companion object {
            const val SHL_BITS_ENTER = 63
            const val SHL_BITS_ID = 43
            const val MASK_ID = 0xFFFFFL
            const val MASK_TIME_MS = 0x7FFFFFFFFFFL
            const val ID_MAX = 0xFFFFF
            const val ID_SLICE = ID_MAX - 1
        }
    }

    /**
     * [Event Descriptions](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview?tab=t.0#heading=h.uxpopqvbjezh)
     */
    private data class TraceEvent(
        val name: String = "",
        val ph: String = "",
        val ts: Long = 0L,
        val pid: Long = 0L,
        val tid: Long = 0L,
        val cat: String = "",
        val args: Any? = null
    ) {

        companion object {
            const val B = "B" // begin
            const val E = "E" // end
        }
    }
}