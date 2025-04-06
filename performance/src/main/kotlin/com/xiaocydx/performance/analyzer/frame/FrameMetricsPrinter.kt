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
import androidx.annotation.RequiresApi
import androidx.core.util.Pools.SynchronizedPool
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver.Companion.DEFAULT_INTERVAL_MILLIS
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

/**
 * [FrameMetricsAggregate]的日志打印
 *
 * @author xcc
 * @date 2025/4/3
 */
class FrameMetricsPrinter(
    /**
     * 接收[FrameMetricsAggregate]的时间间隔
     */
    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) : FrameMetricsReceiver {
    private val json = Json()

    override fun onAvailable(aggregate: FrameMetricsAggregate) {
        val candidate = visitorPool.acquire()
        if (candidate == null) {
            Log.e(TAG, "candidate = null, targetName = ${aggregate.targetName}")
        }
        val visitor = candidate ?: FrameMetricsAggregateVisitor()
        aggregate.accept(visitor)
        Dispatchers.Default.dispatch(EmptyCoroutineContext) {
            var str = ""
            try {
                json.apply(visitor)
                str = json.toString()
            } finally {
                visitorPool.release(visitor)
                if (str.isNotEmpty()) Log.e(TAG, str)
            }
        }
    }

    private class Json {
        private val result = JSONObject()
        private val temp = StringBuilder()
        private val dropNames = DroppedFrames.entries.map { it.name.lowercase() }
        private val avgDropNames = DroppedFrames.entries.map { "avgDroppedDuration-${it.name.lowercase()}" }

        fun apply(visitor: FrameMetricsAggregateVisitor) {
            result.put("targetName", visitor.targetName)
            result.put("intervalMillis", visitor.intervalMillis)
            result.put("renderedFrames", visitor.renderedFrames)
            result.put("avgFps", visitor.avgFps.roundToInt())
            result.put("avgRefreshRate", visitor.avgRefreshRate.roundToInt())
            result.put("droppedFrames", droppedFramesString(visitor))
            if (Build.VERSION.SDK_INT >= 24) {
                avgDropNames.forEach { result.remove(it) }
                DroppedFrames.entries.forEachIndexed { i, drop ->
                    val total = visitor.avgDroppedDurationOf(drop, FrameDuration.Total)
                    if (total == 0L) return@forEachIndexed
                    val str = avgDroppedDurationString(drop, visitor)
                    result.put(avgDropNames[i], str)
                }
            }
        }

        private fun droppedFramesString(visitor: FrameMetricsAggregateVisitor): String {
            temp.clear()
            temp.append("{")
            var total = 0
            val last = DroppedFrames.entries.lastIndex
            DroppedFrames.entries.forEachIndexed { i, drop ->
                val frames = visitor.droppedFramesOf(drop)
                if (drop == DroppedFrames.Total) {
                    total = frames
                    temp.append("total").append(" = ").append(total)
                } else {
                    var rate = if (total > 0) frames.toFloat() / total else 0f
                    rate *= 100
                    rate = (rate * 10).toInt() / 10f
                    temp.append(dropNames[i]).append(" = ").append(rate).append('%')
                }
                if (i != last) temp.append(", ")
            }
            temp.append("}")
            return temp.toString()
        }

        @RequiresApi(24)
        private fun avgDroppedDurationString(
            drop: DroppedFrames,
            visitor: FrameMetricsAggregateVisitor
        ): String {
            temp.clear()
            temp.append("{")
            val last = FrameDuration.entries.lastIndex
            FrameDuration.entries.forEachIndexed { i, id ->
                val ns = visitor.avgDroppedDurationOf(drop, id)
                val ms = ns / FrameMetricsAggregate.NANOS_PER_MILLIS
                temp.append(id.name.lowercase()).append(" = ").append(ms).append("ms")
                if (i != last) temp.append(", ")
            }
            temp.append("}")
            return temp.toString()
        }

        override fun toString(): String {
            return result.toString(2)
        }
    }

    private companion object {
        const val TAG = "FrameMetricsPrinter"
        val visitorPool = SynchronizedPool<FrameMetricsAggregateVisitor>(5)
    }
}