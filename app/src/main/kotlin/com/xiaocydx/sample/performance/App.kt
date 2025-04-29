package com.xiaocydx.sample.performance

import android.app.Application
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.MetricsWriter
import com.xiaocydx.performance.analyzer.anr.ANRMetricsConfig
import com.xiaocydx.performance.analyzer.anr.ANRMetricsPrinter
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.block.BlockMetricsPrinter
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsPrinter

/**
 * @author xcc
 * @date 2025/3/19
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val writer = MetricsWriter(this)
        Performance.init(
            application = this,
            config = Performance.Config(
                frameConfig = FrameMetricsConfig(receivers = listOf(FrameMetricsPrinter())),
                blockConfig = BlockMetricsConfig(receivers = listOf(writer, BlockMetricsPrinter())),
                anrConfig = ANRMetricsConfig(receivers = listOf(writer, ANRMetricsPrinter()))
            )
        )
    }
}