package com.xiaocydx.performance.sample

import android.util.Log
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAccumulation
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver
import com.xiaocydx.performance.analyzer.frame.FrameMetricsReceiver.Companion.DEFAULT_INTERVAL_MILLIS
import com.xiaocydx.performance.analyzer.frame.copy
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author xcc
 * @date 2025/4/3
 */
class FrameMetricsPrinter(
    override val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) : FrameMetricsReceiver {

    override fun onAvailable(accumulation: FrameMetricsAccumulation) {
        val copy = accumulation.copy()
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val obj = JSONObject()
            obj.put("targetName", copy.targetName)
            obj.put("intervalMillis", copy.intervalMillis)
            obj.put("totalFrames", copy.totalFrames)
            obj.put("droppedFrames", copy.droppedFrames)
            obj.put("avgFps", copy.avgFps)
            obj.put("avgDroppedFrames", copy.avgDroppedFrames)
            obj.put("avgRefreshRate", copy.avgRefreshRate)
            Log.e("FrameMetricsPrinter", obj.toString())
        }
    }
}