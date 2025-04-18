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
import com.xiaocydx.performance.plugin.PerformanceExtension
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * @author xcc
 * @date 2025/4/18
 */
internal abstract class GenerateJsonTask : DefaultTask() {

    @TaskAction
    fun taskAction() {
        val ext = PerformanceExtension.getHistory(project)
        val mappingMethodFile = File(ext.mappingMethodFile)
        val mappingSnapshotDir = File(ext.mappingSnapshotDir)
        require(mappingMethodFile.exists()) { "$mappingMethodFile not exists" }
        require(mappingSnapshotDir.exists()) { "$mappingSnapshotDir not exists" }

        val mapping = mappingMethodFile.bufferedReader(Metadata.charset).useLines { lines ->
            lines.map { MethodData.fromOutput(it) }.associateBy { it.id }.toMutableMap()
        }
        val slice = MethodData(
            id = Record.ID_SLICE, access = 0,
            className = "Record", methodName = "slice", desc = ""
        )
        mapping[slice.id] = slice

        val listFiles = mappingSnapshotDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val records = listFiles.associate { file ->
            val record = file.bufferedReader().useLines { lines ->
                lines.map { Record(it.toLong()) }.toList()
            }
            file.name to record
        }

        val gson = Gson()
        val jsonDir = File(mappingSnapshotDir, "json")
        jsonDir.takeIf { !it.exists() }?.mkdirs()
        records.forEach {
            val events = it.value.map { record ->
                val methodData = requireNotNull(mapping[record.id]) { "id = ${record.id}" }
                TraceEvent(
                    name = "${methodData.className}.${methodData.methodName}",
                    ph = if (record.isEnter) TraceEvent.B else TraceEvent.E,
                    ts = record.timeMs * 1000 // to microsecond
                )
            }
            File(jsonDir, "${it.key}.json").bufferedWriter()
                .use { writer -> writer.write(gson.toJson(events)) }
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
        val name: String,
        val ph: String,
        val ts: Long,
        val pid: Long = 0,
        val tid: Long = 0,
        val cat: String = "",
        val args: String = ""
    ) {
        companion object {
            const val B = "B" // begin
            const val E = "E" // end
        }
    }
}