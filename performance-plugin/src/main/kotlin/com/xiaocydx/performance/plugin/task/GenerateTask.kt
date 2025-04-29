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
            metricsDir = File(ext.metricsDir),
            logger = logger,
            parserList = listOf(BlockMetricsParser())
        )
        parseMetricsFiles(context).forEach { pending ->
            val (file, metrics, parser) = pending
            val json = parser.json(file, metrics, context) ?: return@forEach
            val jsonFile = File(context.traceDir, "${file.name}.json")
            jsonFile.bufferedWriter().use { writer -> writer.write(json) }
            logger.lifecycle { "${file.name} [success]: ${jsonFile.absolutePath}" }
        }
    }

    private fun parseMetricsFiles(context: GenerateContext): List<Pending> {
        val outcome = mutableListOf<Pending>()
        context.metricsFiles.forEach { file ->
            val text = file.readText()
            val element = JsonParser.parseString(text)
            val obj = runCatching { element.asJsonObject }.getOrNull()
            val tag = runCatching { obj?.get("tag")?.asString }.getOrNull()
            if (tag.isNullOrEmpty()) {
                logger.lifecycle { "${file.name} [failure]: tag is empty" }
                return@forEach
            }
            context.parserList.forEach action@{
                val clazz = it.match(tag)
                if (clazz == null) {
                    logger.lifecycle { "${file.name} [failure]: tag no match" }
                    return@action
                }
                val metrics = context.gson.fromJson(text, clazz)
                @Suppress("UNCHECKED_CAST")
                outcome.add(Pending(file, metrics, it as MetricsParser<Any>))
            }
        }
        return outcome
    }

    private data class Pending(val file: File, val metrics: Any, val parser: MetricsParser<Any>)
}