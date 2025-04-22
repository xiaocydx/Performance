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

package com.xiaocydx.performance.analyzer.block

import android.app.Application
import android.util.Log
import com.xiaocydx.performance.analyzer.block.BlockMetricsReceiver.Companion.DEFAULT_THRESHOLD_MILLIS
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.File.separator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 往设备写入[BlockMetrics]
 *
 * @author xcc
 * @date 2025/4/15
 */
class BlockMetricsWriter(
    private val application: Application,
    /**
     * 接收[BlockMetrics]的卡顿阈值
     */
    override val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS,
) : BlockMetricsReceiver {

    override fun receive(metrics: BlockMetrics) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val json = print(metrics)
            write(metrics, json)
        }
    }

    private fun print(metrics: BlockMetrics): JSONObject = with(metrics) {
        val json = JSONObject().apply {
            put("pid", pid)
            put("tid", tid)
            put("scene", scene)
            put("lastActivity", lastActivity)
            put("priority", priority)
            put("nice", nice)
            put("createTimeMillis", createTimeMillis)
            put("thresholdMillis", thresholdMillis)
            put("wallDurationMillis", wallDurationMillis)
            put("cpuDurationMillis", cpuDurationMillis)
            put("isRecordEnabled", isRecordEnabled)
            put("metadata", metadata)
        }
        if (stackTrace != null) {
            val cause = BlockMetricsStackTrace()
            cause.stackTrace = stackTrace
            Log.e(TAG, json.toString(2), cause)
        } else {
            Log.e(TAG, json.toString(2))
        }
        return json
    }

    private fun write(metrics: BlockMetrics, json: JSONObject) = with(metrics) {
        if (!snapshot.isAvailable && stackTrace == null) return
        val file = file(metrics)
        json.put("snapshot", JSONArray().apply {
            for (i in 0 until snapshot.size) put(snapshot[i].value)
        })
        json.put("stackTrace", JSONArray().apply {
            stackTrace?.forEach { put(it.toString()) }
        })
        file.bufferedWriter().use { it.write(json.toString(2)) }
    }

    private fun file(metrics: BlockMetrics): File {
        val time = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date(metrics.createTimeMillis))
        } catch (e: Throwable) {
            metrics.createTimeMillis.toString()
        }
        val child = "performance${separator}block${separator}BlockMetrics_${time}"
        val file = File(application.filesDir, child)
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.takeIf { !it.exists() }?.delete()
        return file
    }

    private companion object {
        const val TAG = "BlockMetricsWriter"
    }
}

internal class BlockMetricsStackTrace : RuntimeException()