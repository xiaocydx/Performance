package com.xiaocydx.sample.performance

import android.app.Application
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.block.BlockConfig
import com.xiaocydx.performance.analyzer.block.BlockWriter
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsPrinter

/**
 * @author xcc
 * @date 2025/3/19
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Performance.init(
            application = this,
            config = Performance.Config(
                blockConfig = BlockConfig(receivers = listOf(BlockWriter(context = this))),
                frameConfig = FrameMetricsConfig(receivers = listOf(FrameMetricsPrinter()))
            )
        )
    }
}