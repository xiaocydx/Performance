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

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.xiaocydx.performance.analyzer.toCause
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author xcc
 * @date 2025/4/27
 */
class BlockMetricsPrinter : BlockMetricsReceiver {
    private val gson by lazy {
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(BlockMetrics::class.java, Adapter())
            .create()
    }

    override fun receive(metrics: BlockMetrics) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            Log.e(TAG, gson.toJson(metrics))
            val sampleList = metrics.sampleList.takeLast(3)
            for (i in sampleList.lastIndex downTo 0) {
                val sample = metrics.sampleList[i]
                val removed = sample.threadStat.copy(stack = emptyList())
                val message = sample.copy(threadStat = removed).toString()
                    .replace("Sample", "Sample${i + 1}")
                    .replace(", stack=[]", "")
                Log.e(TAG, message, sample.threadStat.toCause())
            }
        }
    }

    private class Adapter : TypeAdapter<BlockMetrics>() {
        override fun write(out: JsonWriter, metrics: BlockMetrics) {
            out.beginObject()
            out.apply {
                name("scene").value(metrics.scene)
                name("metadata").value(metrics.metadata)
                name("latestActivity").value(metrics.latestActivity)
                name("thresholdMillis").value(metrics.thresholdMillis)
                name("wallDurationMillis").value(metrics.wallDurationMillis)
                name("cpuDurationMillis").value(metrics.cpuDurationMillis)
                name("isRecordEnabled").value(metrics.isRecordEnabled)
            }
            out.endObject()
        }

        override fun read(`in`: JsonReader): BlockMetrics {
            throw UnsupportedOperationException()
        }
    }

    private companion object {
        const val TAG = "BlockMetricsPrinter"
    }
}