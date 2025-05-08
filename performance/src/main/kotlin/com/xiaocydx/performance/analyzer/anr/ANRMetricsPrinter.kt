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

package com.xiaocydx.performance.analyzer.anr

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
 * @date 2025/4/29
 */
class ANRMetricsPrinter : ANRMetricsReceiver {
    private val gson by lazy {
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(ANRMetrics::class.java, Adapter())
            .create()
    }

    override fun receive(metrics: ANRMetrics) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            Log.e(TAG, gson.toJson(metrics))
            val anrSample = metrics.anrSample
            val removed = anrSample.threadStat.copy(stack = emptyList())
            val message = anrSample.copy(threadStat = removed).toString()
                .replace("Sample", "ANRSample")
                .replace(", stack=[]", "")
            Log.e(TAG, message, anrSample.threadStat.toCause())
        }
    }

    private class Adapter : TypeAdapter<ANRMetrics>() {
        override fun write(out: JsonWriter, metrics: ANRMetrics) {
            out.beginObject()
            out.apply {
                name("latestActivity").value(metrics.latestActivity)
                name("blockThresholdMillis").value(metrics.blockThresholdMillis)
                name("sampleIntervalMillis").value(metrics.sampleIntervalMillis)
                name("isRecordEnabled").value(metrics.isRecordEnabled)
                name("history.size").value(metrics.history.size)
                name("future.size").value(metrics.future.size)
            }
            out.endObject()
        }

        override fun read(`in`: JsonReader): ANRMetrics {
            throw UnsupportedOperationException()
        }
    }

    private companion object {
        const val TAG = "ANRMetricsPrinter"
    }
}