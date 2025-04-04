package com.xiaocydx.performance.sample

import android.util.Log
import com.xiaocydx.performance.analyzer.frame.DropLevel
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAggregate
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver.Companion.DEFAULT_INTERVAL_MILLIS
import com.xiaocydx.performance.analyzer.frame.copy
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt

/**
 * @author xcc
 * @date 2025/4/3
 */
class FrameMetricsPrinter(
    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) : FrameMetricsReceiver {

    override fun onAvailable(aggregate: FrameMetricsAggregate) {
        val copy = aggregate.copy()
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val obj = JSONObject()
            obj.put("targetName", copy.targetName)
            obj.put("intervalMillis", copy.intervalMillis)
            obj.put("renderedFrames", copy.renderedFrames)
            obj.put("avgFps", copy.avgFps.roundToInt())
            obj.put("avgRefreshRate", copy.avgRefreshRate.roundToInt())

            val droppedObj = JSONObject()
            droppedObj.put("total", copy.droppedFrames)
            DropLevel.entries.forEach {
                val frames = copy.getDroppedFrames(it)
                val rate = frames.toFloat() / copy.droppedFrames
                droppedObj.put(it.name.lowercase(), "${(rate * 100).roundToInt()}%")
            }

            obj.put("droppedFrames", droppedObj)
            Log.e("FrameMetricsPrinter", obj.toString())
        }
    }
}