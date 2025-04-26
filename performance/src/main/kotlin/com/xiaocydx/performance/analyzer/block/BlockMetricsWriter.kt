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
import com.xiaocydx.performance.runtime.history.sample.Sample
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
            print(metrics, write(metrics))
        }
    }

    private fun write(metrics: BlockMetrics): JSONObject = with(metrics) {
        val dataJson = JSONObject().apply {
            put("pid", pid)
            put("tid", tid)
            put("scene", scene)
            put("latestActivity", latestActivity)
            put("priority", priority)
            put("nice", nice)
            put("createTimeMillis", createTimeMillis)
            put("thresholdMillis", thresholdMillis)
            put("wallDurationMillis", wallDurationMillis)
            put("cpuDurationMillis", cpuDurationMillis)
            put("isRecordEnabled", isRecordEnabled)
            put("metadata", metadata)
            put("snapshot", JSONArray().apply {
                for (i in 0 until snapshot.size) put(snapshot[i].value)
            })
            put("sampleList", JSONArray().apply {
                metrics.sampleList.forEach { sample -> put(sample.toJSONObject()) }
            })
        }
        val result = JSONObject()
        result.put("tag", "BlockMetrics")
        result.put("data", dataJson)
        file(metrics).bufferedWriter().use { it.write(result.toString(2)) }
        return dataJson
    }

    private fun print(metrics: BlockMetrics, json: JSONObject) {
        json.remove("snapshot")
        json.remove("sampleList")
        Log.e(TAG, json.toString(2))
        for (i in metrics.sampleList.lastIndex downTo 0) {
            val sample = metrics.sampleList[i]
            val cause = BlockMetricsSampleStack()
            cause.stackTrace = sample.threadStack.toTypedArray()
            Log.e(TAG, "Sample${i + 1} { threadState=${sample.threadState} }", cause)
        }
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

    private fun Sample.toJSONObject() = JSONObject().apply {
        put("uptimeMillis", uptimeMillis)
        put("threadState", threadState.name)
        put("threadStack", JSONArray().apply {
            threadStack.forEach { put(it.toString()) }
        })
    }

    private companion object {
        const val TAG = "BlockMetricsWriter"
    }
}

internal class BlockMetricsSampleStack() : RuntimeException()