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

package com.xiaocydx.performance.analyzer.frame

import android.os.Build
import android.util.Log
import androidx.core.util.Pools.SynchronizedPool
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver.Companion.DEFAULT_INTERVAL_MILLIS
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

/**
 * 打印[FrameMetricsAggregate]
 *
 * @author xcc
 * @date 2025/4/3
 */
class FrameMetricsPrinter(
    /**
     * 接收[FrameMetricsAggregate]的时间间隔
     */
    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) : FrameMetricsReceiver {
    private val json = Json()

    override fun receive(aggregate: FrameMetricsAggregate) {
        var visitor = visitorPool.acquire()
        if (visitor == null) {
            visitor = FrameMetricsVisitor()
            Log.e(TAG, "visitor = null, targetName = ${aggregate.targetName}")
        }
        aggregate.accept(visitor)
        Dispatchers.Default.dispatch(EmptyCoroutineContext) {
            val outcome = json.apply(visitor)
            visitorPool.release(visitor)
            Log.e(TAG, outcome)
        }
    }

    private class Json {
        private val dropNames = DroppedFrames.entries.map { it.name.lowercase() }
        private val avgDropNames = DroppedFrames.entries.map { "avgDroppedDuration-${it.name.lowercase()}" }

        fun apply(visitor: FrameMetricsVisitor): String {
            var json = jsonPool.acquire()
            if (json == null) {
                json = JSONObject()
                Log.e(TAG, "json = null, targetName = ${visitor.targetName}")
            }

            var str = strPool.acquire()
            if (str == null) {
                str = StringBuilder()
                Log.e(TAG, "str = null, targetName = ${visitor.targetName}")
            }

            json.put("targetName", visitor.targetName)
            json.put("intervalMillis", visitor.intervalMillis)
            json.put("renderedFrames", visitor.renderedFrames)
            json.put("avgFps", visitor.avgFps.roundToInt())
            json.put("avgRefreshRate", visitor.avgRefreshRate.roundToInt())
            json.put("droppedFrames", droppedFramesString(str, visitor))

            avgDropNames.forEach { json.remove(it) }
            DroppedFrames.entries.forEachIndexed { i, drop ->
                val total = visitor.avgDroppedDurationOf(drop, FrameDuration.Total)
                if (total == 0L) return@forEachIndexed
                json.put(avgDropNames[i], avgDroppedDurationString(str, drop, visitor))
            }

            val outcome = json.toString(2)
            jsonPool.release(json)
            strPool.release(str)
            return outcome
        }

        private fun droppedFramesString(str: StringBuilder, visitor: FrameMetricsVisitor): String {
            str.clear()
            str.append("{")
            var total = 0
            val last = DroppedFrames.entries.lastIndex
            DroppedFrames.entries.forEachIndexed { i, drop ->
                val frames = visitor.droppedFramesOf(drop)
                if (drop == DroppedFrames.Total) {
                    total = frames
                    str.append("total").append(" = ").append(total)
                } else {
                    var rate = if (total > 0) frames.toFloat() / total else 0f
                    rate *= 100
                    rate = (rate * 10).toInt() / 10f
                    str.append(dropNames[i]).append(" = ").append(rate).append('%')
                }
                if (i != last) str.append(", ")
            }
            str.append("}")
            return str.toString()
        }

        private fun avgDroppedDurationString(
            str: StringBuilder,
            drop: DroppedFrames,
            visitor: FrameMetricsVisitor
        ): String {
            str.clear()
            str.append("{")
            val sdk = Build.VERSION.SDK_INT
            val last = FrameDuration.entries.lastIndex
            FrameDuration.entries.forEachIndexed { i, id ->
                if (sdk < id.api) return@forEachIndexed
                val ns = visitor.avgDroppedDurationOf(drop, id)
                val ms = ns / FrameMetricsAggregate.NANOS_PER_MILLIS
                str.append(id.name.lowercase()).append(" = ").append(ms).append("ms")
                if (i != last) str.append(", ")
            }
            str.append("}")
            return str.toString()
        }
    }

    private companion object {
        const val TAG = "FrameMetricsPrinter"
        const val POOL_SIZE = 5
        val visitorPool = SynchronizedPool<FrameMetricsVisitor>(POOL_SIZE)
        val jsonPool = SynchronizedPool<JSONObject>(POOL_SIZE)
        val strPool = SynchronizedPool<StringBuilder>(POOL_SIZE)
    }
}