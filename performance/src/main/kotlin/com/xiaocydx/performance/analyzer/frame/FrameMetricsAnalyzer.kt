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

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.annotation.CallSuper
import androidx.core.view.doOnAttach
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.frame.api16.FrameMetricsAnalyzerApi16
import com.xiaocydx.performance.analyzer.frame.api24.FrameMetricsAnalyzerApi24
import com.xiaocydx.performance.runtime.activity.ActivityEvent
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * @author xcc
 * @date 2025/4/5
 */
internal abstract class FrameMetricsAnalyzer(host: Performance.Host) : Analyzer(host) {
    protected val frameMetricsListeners = HashMap<Int, FrameMetricsListener>()
    @Volatile protected var defaultRefreshRate = 60.0f; private set

    @CallSuper
    override fun init() {
        // TODO: 补充isStopped拦截
        coroutineScope.launch {
            host.activityEvent.collect {
                val activity = host.getActivity(it.activityKey)
                when (it) {
                    is ActivityEvent.Resumed -> if (activity != null) {
                        // 在Resumed初始化，避免过早调用window.decorView，触发构建逻辑
                        var listener = frameMetricsListeners[it.activityKey]
                        if (listener != null) return@collect
                        val window = activity.window
                        window.decorView.doOnAttach {
                            defaultRefreshRate = window.getRefreshRate(defaultRefreshRate)
                        }
                        listener = createListener(activity).attach()
                        frameMetricsListeners[it.activityKey] = listener
                    }
                    is ActivityEvent.Stopped -> {
                        frameMetricsListeners[it.activityKey]?.forceMakeEnd()
                    }
                    is ActivityEvent.Destroyed -> {
                        frameMetricsListeners.remove(it.activityKey)?.detach()
                    }
                    else -> return@collect
                }
            }
        }.invokeOnCompletion {
            frameMetricsListeners.forEach { it.value.detach() }
            frameMetricsListeners.clear()
        }
    }

    protected abstract fun createListener(activity: Activity?): FrameMetricsListener

    protected fun Window.getRefreshRate(default: Float): Float {
        var refreshRate = default
        decorView.display?.let {
            val displayRefreshRate = it.refreshRate
            if (displayRefreshRate >= 30.0f) refreshRate = displayRefreshRate
        }
        return refreshRate
    }

    protected abstract class FrameMetricsListener(activity: Activity?, config: FrameMetricsConfig) {
        protected val activityRef = activity?.let(::WeakReference)
        protected val activityKey = activity?.hashCode()?.toLong() ?: 0L
        protected val activityName = activity?.javaClass?.simpleName ?: ""
        protected val frameMetricsAggregators = config.receivers.map(::FrameMetricsAggregator)

        abstract fun attach(): FrameMetricsListener

        abstract fun detach(): FrameMetricsListener

        abstract fun forceMakeEnd(): FrameMetricsListener

        protected inline fun dispatchAggregators(action: (FrameMetricsAggregator) -> Unit) {
            for (i in frameMetricsAggregators.indices) action(frameMetricsAggregators[i])
        }
    }

    companion object {

        fun create(
            host: Performance.Host,
            config: FrameMetricsConfig,
        ) = if (Build.VERSION.SDK_INT >= 24) {
            FrameMetricsAnalyzerApi24(host, config)
        } else {
            FrameMetricsAnalyzerApi16(host, config)
        }
    }
}