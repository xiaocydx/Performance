package com.xiaocydx.performance.sample

import android.app.Application
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.FrameConfig

/**
 * @author xcc
 * @date 2025/3/19
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val frameConfig = FrameConfig(receivers = listOf(FrameMetricsPrinter()))
        val performanceConfig = Performance.Config(frameConfig = frameConfig)
        Performance.init(performanceConfig, this)
    }
}