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
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.watcher.looper.MainLooperCallback
import kotlinx.coroutines.launch

/**
 * @author xcc
 * @date 2025/4/5
 */
internal class FrameMetricsAnalyzerApi16(
    host: Performance.Host,
    private val config: FrameMetricsConfig,
) : FrameMetricsAnalyzer(host) {
    private val choreographerFrameInfo = ChoreographerFrameInfo()

    override fun init() {
        val job = coroutineScope.launch {
            choreographerFrameInfo.init()
            choreographerFrameInfo.doOnFrameEnd { }
        }
        job.invokeOnCompletion {
            choreographerFrameInfo.doOnFrameEnd(null)
        }
        super.init()
    }

    override fun createListener(activity: Activity?): FrameMetricsListener {
        return FrameMetricsListenerImpl(activity)
    }

    override fun getCallback(): MainLooperCallback {
        return choreographerFrameInfo.callback
    }

    private inner class FrameMetricsListenerImpl(activity: Activity?) :
            FrameMetricsListener(activity, config),
            ViewTreeObserver.OnPreDrawListener,
            ViewTreeObserver.OnDrawListener {
        private val frameInfo = FrameInfo()

        override fun attach() = apply {
            val window = activityRef?.get()?.window ?: return@apply
            window.decorView.viewTreeObserver.addOnPreDrawListener(this)
            window.decorView.viewTreeObserver.addOnDrawListener(this)
        }

        override fun detach() = apply {
            val window = activityRef?.get()?.window ?: return@apply
            window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
            window.decorView.viewTreeObserver.removeOnDrawListener(this)
            forceMakeEnd()
        }

        override fun forceMakeEnd() = apply {
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