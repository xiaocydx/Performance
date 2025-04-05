package com.xiaocydx.performance.sample

import android.app.Application
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsPrinter

/**
 * @author xcc
 * @date 2025/3/19
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val frameConfig = FrameMetricsConfig(receivers = listOf(FrameMetricsPrinter()))
        val performanceConfig = Performance.Config(frameMetrics = frameConfig)
        Performance.init(performanceConfig, this)
    }
}