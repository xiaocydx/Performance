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

package com.xiaocydx.performance.analyzer.frame.api16

import android.app.Activity
import android.view.ViewTreeObserver
import androidx.core.view.doOnAttach
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import com.xiaocydx.performance.watcher.looper.MainLooperCallback
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * @author xcc
 * @date 2025/4/5
 */
internal class FrameMetricsAnalyzerApi16(
    private val host: Performance.Host,
    private val config: FrameMetricsConfig
) : FrameMetricsAnalyzer {
    private val coroutineScope = host.createMainScope()
    private val frameMetricsListeners = HashMap<Int, FrameMetricsListener>()
    private val choreographerFrameInfo = ChoreographerFrameInfo()
    @Volatile private var defaultRefreshRate = 60.0f

    override fun init() {
        val job = coroutineScope.launch {
            choreographerFrameInfo.init()
            choreographerFrameInfo.doOnFrameEnd {
                // TODO: 通知frameMetricsListeners
            }
            host.activityEvent.collect {
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
                    is ActivityEvent.Stopped -> {
                        frameMetricsListeners[it.activityKey]?.forceMakeEnd()
                    }
                    is ActivityEvent.Destroyed -> {
                        frameMetricsListeners.remove(it.activityKey)?.detach()
                    }
                    else -> return@collect
                }
            }
        }

        job.invokeOnCompletion {
            // TODO: 移除frameMetricsListeners
            choreographerFrameInfo.doOnFrameEnd(null)
        }
    }

    override fun cancel() {
        coroutineScope.cancel()
    }

    override fun getCallback(): MainLooperCallback {
        return choreographerFrameInfo.callback
    }

    private inner class FrameMetricsListener(
        activity: Activity?,
    ) : ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnDrawListener {
        private val activityRef = activity?.let(::WeakReference)
        private val activityKey = activity?.hashCode()?.toLong() ?: 0L
        private val activityName = activity?.javaClass?.simpleName ?: ""
        private val frameInfo = FrameInfo()

        fun attach() = apply {
            val window = activityRef?.get()?.window ?: return@apply
            window.decorView.viewTreeObserver.addOnPreDrawListener(this)
            window.decorView.viewTreeObserver.addOnDrawListener(this)
        }

        fun detach() = apply {
            val window = activityRef?.get()?.window ?: return@apply
            window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
            window.decorView.viewTreeObserver.removeOnDrawListener(this)
            forceMakeEnd()
        }

        fun forceMakeEnd() {

        }

        override fun onPreDraw(): Boolean {
            frameInfo.markPreDrawStart()
            return true
        }

        override fun onDraw() {
            frameInfo.markDrawStart()
        }
    }
}