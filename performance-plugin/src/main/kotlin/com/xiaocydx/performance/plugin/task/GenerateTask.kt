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
import com.google.gson.JsonParser
import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.PerformanceExtension
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import com.xiaocydx.performance.plugin.task.GenerateTask.Record.Companion.ID_SLICE
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author xcc
 * @date 2025/4/18
 */
internal abstract class GenerateTask : DefaultTask() {
    private val logger = Logger(javaClass)

    @TaskAction
    fun taskAction() {
        val ext = PerformanceExtension.getHistory(project).setDefaultProperty(project)
        val context = GenerateContext(File(ext.mappingMethodFile), File(ext.snapshotDir))
        parseSnapshotFiles(context).forEach {
            val json = when (val data = it.data) {
                is BlockMetrics -> toTraceEventJson(it.file, data, context)
                else -> return@forEach
            } ?: return@forEach
            val jsonFile = File(context.jsonDir, "${it.file.name}.json")
            jsonFile.bufferedWriter().use { writer -> writer.write(json) }
            logger.lifecycle { "${it.file.name} [success]: ${jsonFile.absolutePath}" }
        }
    }

    private fun parseSnapshotFiles(context: GenerateContext): List<Pair> {
        val outcome = mutableListOf<Pair>()
        context.snapshotFiles.forEach { file ->
            val element = JsonParser.parseString(file.readText())
            val obj = runCatching { element.asJsonObject }.getOrNull()
            val tag = runCatching { obj?.get("tag")?.asString }.getOrNull()
            val dataJson = obj?.get("data")?.toString()
            if (tag.isNullOrEmpty()) {
                logger.lifecycle { "${file.name} [failure]: tag is empty" }
                return@forEach
            }
            if (dataJson.isNullOrEmpty()) {
                logger.lifecycle { "${file.name} [failure]: data is empty" }
                return@forEach
            }
            val dataClass = when (tag) {
                BlockMetrics.TAG -> BlockMetrics::class.java
                else -> return@forEach logger.lifecycle { "${file.name} [failure]: tag no match" }
            }
            outcome.add(Pair(file, context.gson.fromJson(dataJson, dataClass)))
        }
        return outcome
    }

    private fun toTraceEventJson(
        file: File,
        metrics: BlockMetrics,
        context: GenerateContext
    ): String? {
        if (metrics.snapshot.isEmpty()) {
            logger.lifecycle { "${file.name} [failure]: snapshot is empty" }
            return null
        }
        val args = metrics.copy(snapshot = emptyList())
        val events = metrics.snapshot.map {
            val record = Record(it)
            var methodData = context.mappingMethod[record.id]
            if (methodData == null) {
                if (record.id != ID_SLICE) {
                    logger.lifecycle { "${file.name} [failure]: id = ${record.id} not exists" }
                    return null
                }
                methodData = MethodData(
                    id = ID_SLICE, access = 0,
                    className = BlockMetrics.TAG,
                    methodName = metrics.scene,
                    desc = ""
                )
            }
            val className = methodData.className.replace("/", ".")
            TraceEvent(
                name = "${className}.${methodData.methodName}",
                ph = if (record.isEnter) TraceEvent.B else TraceEvent.E,
                ts = record.timeMs * 1000, // to microsecond
                pid = metrics.pid.toLong(),
                tid = metrics.tid.toLong(),
                cat = metrics.scene,
                args = if (record.id == ID_SLICE) args else null
            )
        }
        return context.gson.toJson(events)
    }

    private class GenerateContext(mappingFile: File, snapshotDir: File) {
        val gson = Gson()
        val jsonDir = File(snapshotDir, "json")
        val snapshotFiles = snapshotDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val mappingMethod = mappingFile.bufferedReader(Metadata.charset).useLines { lines ->
            lines.map { MethodData.fromOutput(it) }.associateBy { it.id }
        }

        init {
            require(mappingFile.exists()) { "$mappingFile not exists" }
            require(snapshotDir.exists()) { "$snapshotDir not exists" }
            jsonDir.mkdirs()
        }
    }

    private class Pair(val file: File, val data: Any)

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
        val sampleState: String = "",
        val sampleStack: List<String> = emptyList()
    ) {
        companion object {
            const val TAG = "BlockMetrics"
        }
    }

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