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

package com.xiaocydx.performance.analyzer

import android.app.Application
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.xiaocydx.performance.analyzer.anr.ANRMetrics
import com.xiaocydx.performance.analyzer.anr.ANRMetricsReceiver
import com.xiaocydx.performance.analyzer.block.BlockMetrics
import com.xiaocydx.performance.analyzer.block.BlockMetricsReceiver
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.File.separator
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 往设备写入[Metrics]
 *
 * @author xcc
 * @date 2025/4/29
 */
class MetricsWriter(
    private val application: Application
) : BlockMetricsReceiver, ANRMetricsReceiver {
    private val gson by lazy {
        GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(StackTraceElement::class.java, StackTraceElementSerializer())
            .create()!!
    }

    override fun receive(metrics: BlockMetrics) {
        dispatch { write(dirName = "block", metrics) }
    }

    override fun receive(metrics: ANRMetrics) {
        dispatch { write(dirName = "anr", metrics) }
    }

    private fun dispatch(action: () -> Unit) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext, action)
    }

    private fun write(dirName: String, metrics: Metrics) {
        file(dirName, metrics).bufferedWriter().use { gson.toJson(metrics, it) }
    }

    private fun file(dirName: String, metrics: Metrics): File {
        val name = metrics.tag_createTime()
        val child = "performance$separator${dirName}$separator${name}"
        val file = File(application.filesDir, child)
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.takeIf { !it.exists() }?.delete()
        return file
    }

    @Suppress("FunctionName")
    private fun Metrics.tag_createTime(): String {
        val createTime = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date(createTimeMillis))
        } catch (e: Throwable) {
            createTimeMillis.toString()
        }
        return "${tag}_${createTime}"
    }

    private class StackTraceElementSerializer : JsonSerializer<StackTraceElement> {
        override fun serialize(
            src: StackTraceElement,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }
}