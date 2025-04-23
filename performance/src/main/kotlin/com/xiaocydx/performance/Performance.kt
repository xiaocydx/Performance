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
import android.app.ActivityManager
import android.app.Application
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity.ACTIVITY_SERVICE
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.anr.ANRMetricsAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.stable.IdleHandlerAnalyzer
import com.xiaocydx.performance.runtime.activity.ActivityEvent
import com.xiaocydx.performance.runtime.activity.ActivityWatcher
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.looper.CompositeLooperCallback
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.LooperWatcher
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
        host.application = application
        activityWatcher.init(application)

        ANRMetricsAnalyzer(host).start()
        IdleHandlerAnalyzer(host).start()
        config.blockConfig?.let { BlockMetricsAnalyzer(host, it).start() }
        config.frameConfig?.let { FrameMetricsAnalyzer.create(host, it).start() }

        host.callbacks.immutable()
        LooperWatcher.init(host, callback = host.callbacks)
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }
        private val defaultThread by lazy { HandlerThread("PerformanceDefaultThread").apply { start() } }
        val callbacks = CompositeLooperCallback()
        lateinit var application: Application

        override val dumpLooper by lazy { dumpThread.looper!! }

        override val defaultLooper by lazy { defaultThread.looper!! }

        override val ams by lazy { application.getSystemService(ACTIVITY_SERVICE) as ActivityManager }

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

        override fun addCallback(callback: LooperCallback) {
            callbacks.add(callback)
        }

        override fun removeCallback(callback: LooperCallback) {
            callbacks.remove(callback)
        }

        override fun needHistory(analyzer: Analyzer) {
            History.init()
        }
    }

    internal interface Host {

        val dumpLooper: Looper

        val defaultLooper: Looper

        val ams: ActivityManager

        val activityEvent: SharedFlow<ActivityEvent>

        fun createMainScope(): CoroutineScope

        @MainThread
        fun getActivity(key: Int): Activity?

        @MainThread
        fun getLastActivity(): Activity?

        @MainThread
        fun addCallback(callback: LooperCallback)

        @MainThread
        fun removeCallback(callback: LooperCallback)

        @MainThread
        fun needHistory(analyzer: Analyzer)
    }

    data class Config(
        val blockConfig: BlockMetricsConfig? = null,
        val frameConfig: FrameMetricsConfig? = null,
    ) {

        internal fun checkProperty() {
            blockConfig?.checkProperty()
            frameConfig?.checkProperty()
        }
    }
}