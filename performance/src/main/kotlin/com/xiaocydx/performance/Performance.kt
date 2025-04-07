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

package com.xiaocydx.performance

import android.app.Activity
import android.app.Application
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.MainThread
import com.xiaocydx.performance.analyzer.anr.ANRAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.jank.JankAnalyzer
import com.xiaocydx.performance.analyzer.stable.ActivityResumedIdleAnalyzer
import com.xiaocydx.performance.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import com.xiaocydx.performance.watcher.activity.ActivityWatcher
import com.xiaocydx.performance.watcher.looper.CompositeMainLooperCallback
import com.xiaocydx.performance.watcher.looper.MainLooperWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow

/**
 * @author xcc
 * @date 2025/3/19
 */
object Performance {
    private val host = HostImpl()
    private val activityWatcher = ActivityWatcher()
    private var isInitialized = false

    @MainThread
    fun init(config: Config, application: Application) {
        assertMainThread()
        if (isInitialized) return
        isInitialized = true
        config.checkProperty()

        ReferenceQueueDaemon().start()

        activityWatcher.init(application)
        ActivityResumedIdleAnalyzer(host).init()

        val callbacks = CompositeMainLooperCallback()
        callbacks.add(ANRAnalyzer().init())
        callbacks.add((JankAnalyzer(host).init(threshold = 300L)))
        if (config.frameMetrics.receivers.isNotEmpty()) {
            val analyzer = FrameMetricsAnalyzer.create(host, config.frameMetrics)
            analyzer.init()
            analyzer.getCallback()?.let(callbacks::add)
        }
        MainLooperWatcher.init(host, callbacks)
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }
        private val defaultThread by lazy { HandlerThread("PerformanceDefaultThread").apply { start() } }

        override val mainLooper = Looper.getMainLooper()!!

        override val dumpLooper by lazy { dumpThread.looper!! }

        override val defaultLooper by lazy { defaultThread.looper!! }

        override val activityEvent get() = activityWatcher.event

        override fun createMainScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob(parentJob) + Dispatchers.Main.immediate)
        }

        override fun getActivity(key: Int): Activity? {
            return activityWatcher.getActivity(key)
        }

        override fun getLastActivity(): Activity? {
            return activityWatcher.getLastActivity()
        }
    }

    internal interface Host {
        val mainLooper: Looper

        val dumpLooper: Looper

        val defaultLooper: Looper

        val activityEvent: SharedFlow<ActivityEvent>

        fun createMainScope(): CoroutineScope

        @MainThread
        fun getActivity(key: Int): Activity?

        @MainThread
        fun getLastActivity(): Activity?
    }

    data class Config(val frameMetrics: FrameMetricsConfig = FrameMetricsConfig()) {
        internal fun checkProperty() {
            frameMetrics.checkProperty()
        }
    }
}