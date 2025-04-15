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
import com.xiaocydx.performance.analyzer.block.BlockAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.stable.ActivityResumedIdleAnalyzer
import com.xiaocydx.performance.runtime.activity.ActivityEvent
import com.xiaocydx.performance.runtime.activity.ActivityWatcher
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.runtime.looper.CompositeMainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperWatcher
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
    fun init(application: Application, config: Config) {
        assertMainThread()
        if (isInitialized) return
        isInitialized = true
        config.checkProperty()

        ReferenceQueueDaemon().start()
        activityWatcher.init(application)
        ActivityResumedIdleAnalyzer(host).init()

        // ANRAnalyzer(host).init()
        if (config.blockConfig.receivers.isNotEmpty()) {
            BlockAnalyzer(host, config.blockConfig).init()
        }
        if (config.frameConfig.receivers.isNotEmpty()) {
            FrameMetricsAnalyzer.create(host, config.frameConfig).init()
        }

        host.callbacks.immutable()
        MainLooperWatcher.init(host, callback = host.callbacks)
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }
        private val defaultThread by lazy { HandlerThread("PerformanceDefaultThread").apply { start() } }
        val callbacks = CompositeMainLooperCallback()

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

        override fun addCallback(callback: MainLooperCallback) {
            callbacks.add(callback)
        }

        override fun removeCallback(callback: MainLooperCallback) {
            callbacks.remove(callback)
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

        @MainThread
        fun addCallback(callback: MainLooperCallback)

        @MainThread
        fun removeCallback(callback: MainLooperCallback)
    }

    data class Config(
        val blockConfig: BlockConfig = BlockConfig(),
        val frameConfig: FrameMetricsConfig = FrameMetricsConfig(),
    ) {
        internal fun checkProperty() {
            blockConfig.checkProperty()
            frameConfig.checkProperty()
        }
    }
}