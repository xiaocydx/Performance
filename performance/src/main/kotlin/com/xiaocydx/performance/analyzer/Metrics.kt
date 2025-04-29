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
import android.os.Process
import com.google.gson.GsonBuilder
import com.xiaocydx.performance.runtime.history.sample.ThreadStat
import java.io.File
import java.io.File.separator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * @author xcc
 * @date 2025/4/27
 */
interface Metrics {

    /**
     * 可用于区分解析逻辑
     */
    val tag: String

    /**
     * [Process.myPid]
     */
    val pid: Int

    /**
     * [Process.myTid]
     */
    val tid: Int

    /**
     * 可用于定义文件名
     */
    val createTimeMillis: Long
}

internal fun ThreadStat.toCause(): RuntimeException {
    val cause = RuntimeException()
    cause.stackTrace = trace.toTypedArray()
    return cause
}

internal class MetricsOutput(private val application: Application) {

    fun write(dirName: String, metrics: Metrics) {
        val json = gson.toJson(metrics)
        file(dirName, metrics).bufferedWriter().use { it.write(json) }
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

    private companion object {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()!!
    }
}