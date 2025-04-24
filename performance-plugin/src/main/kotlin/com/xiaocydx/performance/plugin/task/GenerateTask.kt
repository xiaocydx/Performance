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

import com.google.gson.JsonParser
import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.PerformanceExtension
import com.xiaocydx.performance.plugin.generate.BlockMetricsParser
import com.xiaocydx.performance.plugin.generate.GenerateContext
import com.xiaocydx.performance.plugin.generate.MetricsParser
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
        val context = GenerateContext(
            mappingFile = File(ext.mappingMethodFile),
            snapshotDir = File(ext.snapshotDir),
            logger = logger,
            parserList = listOf(BlockMetricsParser())
        )
        parseSnapshotFiles(context).forEach { pending ->
            val (file, data, parser) = pending
            val json = parser.toTraceEventsJson(file, data, context) ?: return@forEach
            val jsonFile = File(context.jsonDir, "${file.name}.json")
            jsonFile.bufferedWriter().use { writer -> writer.write(json) }
            logger.lifecycle { "${file.name} [success]: ${jsonFile.absolutePath}" }
        }
    }

    private fun parseSnapshotFiles(context: GenerateContext): List<Pending> {
        val outcome = mutableListOf<Pending>()
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
            context.parserList.forEach action@{
                val dataClass = it.matchDataClass(tag)
                if (dataClass == null) {
                    logger.lifecycle { "${file.name} [failure]: tag no match" }
                    return@action
                }
                val data = context.gson.fromJson(dataJson, dataClass)
                @Suppress("UNCHECKED_CAST")
                outcome.add(Pending(file, data, it as MetricsParser<Any>))
            }
        }
        return outcome
    }

    private data class Pending(val file: File, val data: Any, val parser: MetricsParser<Any>)
}