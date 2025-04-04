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
import android.os.Handler
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.view.doOnAttach
import com.xiaocydx.performance.Cancellable
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.log
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.ref.WeakReference
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/3
 */
@RequiresApi(24)
internal class FrameAnalyzer24(
    config: FrameConfig,
    private val host: Performance.Host
) : Cancellable {
    private val coroutineScope = host.createMainScope()
    private val frameMetricsHandler = Handler(host.defaultLooper)
    private val frameMetricsListeners = HashMap<Int, FrameMetricsListener>()
    private val frameMetricsAccumulators = config.receivers.map(::FrameMetricsAccumulator)
    @Volatile private var defaultRefreshRate = 60.0f

    fun init() {
        val job = host.activityEvent.onEach {
            val activity = host.getActivity(it.activityKey)
            when (it) {
                is ActivityEvent.Created -> if (activity != null) {
                    val window = activity.window
                    window.decorView.doOnAttach {
                        defaultRefreshRate = window.getRefreshRate(defaultRefreshRate)
                    }
                    val listener = FrameMetricsListener(activity).attach()
                    frameMetricsListeners[it.activityKey] = listener
                }
                is ActivityEvent.Destroyed -> {
                    frameMetricsListeners.remove(it.activityKey)?.detach()
                }
                else -> return@onEach
            }
        }.launchIn(coroutineScope)

        job.invokeOnCompletion {
            frameMetricsListeners.forEach { it.value.detach() }
            frameMetricsListeners.clear()
        }
    }

    override fun cancel() {
        coroutineScope.cancel()
    }

    @AnyThread
    private fun Window.getRefreshRate(default: Float): Float {
        var refreshRate = default
        decorView.display?.let {
            val displayRefreshRate = it.refreshRate
            if (displayRefreshRate >= 30.0f) refreshRate = displayRefreshRate
        }
        return refreshRate
    }

    private inner class FrameMetricsListener(
        activity: Activity?,
    ) : Window.OnFrameMetricsAvailableListener {
        private val activityRef = activity?.let(::WeakReference)
        private val activityName = activity?.javaClass?.simpleName ?: ""

        @MainThread
        fun attach() = apply {
            val window = activityRef?.get()?.window ?: return@apply
            window.addOnFrameMetricsAvailableListener(this, frameMetricsHandler)
        }

        @MainThread
        fun detach() = apply {
            activityRef?.get()?.window?.removeOnFrameMetricsAvailableListener(this)
        }

        @WorkerThread
        override fun onFrameMetricsAvailable(
            window: Window,
            frameMetrics: FrameMetrics,
            dropCountSinceLastInvocation: Int
        ) {
            val refreshRate = window.getRefreshRate(defaultRefreshRate)
            val timeMillis = measureTimeMillis {
                dispatchAccumulators { it.makeStart(activityName, frameMetrics) }
                dispatchAccumulators { it.accumulate(refreshRate, frameMetrics) }
                dispatchAccumulators { it.makeEnd() }
            }
            log { "FrameMetricsListener dispatchAccumulators timeMillis = $timeMillis" }
        }

        @WorkerThread
        private inline fun dispatchAccumulators(action: (FrameMetricsAccumulator) -> Unit) {
            for (i in frameMetricsAccumulators.indices) action(frameMetricsAccumulators[i])
        }
    }
}