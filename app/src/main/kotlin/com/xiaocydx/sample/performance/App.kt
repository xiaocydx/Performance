package com.xiaocydx.sample.performance

import android.app.Application
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.anr.ANRMetricsConfig
import com.xiaocydx.performance.analyzer.anr.ANRMetricsWriter
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.block.BlockMetricsPrinter
import com.xiaocydx.performance.analyzer.block.BlockMetricsWriter
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
                frameConfig = FrameMetricsConfig(
                    receivers = listOf(FrameMetricsPrinter())
                ),
                blockConfig = BlockMetricsConfig(
                    receivers = listOf(
                        BlockMetricsPrinter(),
                        BlockMetricsWriter(application = this)
                    )
                ),
                anrConfig = ANRMetricsConfig(
                    receivers = listOf(ANRMetricsWriter())
                )
            )
        )
    }
}