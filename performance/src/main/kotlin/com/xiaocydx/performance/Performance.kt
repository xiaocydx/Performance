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
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity.ACTIVITY_SERVICE
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.anr.ANRMetricsAnalyzer
import com.xiaocydx.performance.analyzer.anr.ANRMetricsConfig
import com.xiaocydx.performance.analyzer.block.BlockMetricsAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.stable.IdleHandlerAnalyzer
import com.xiaocydx.performance.runtime.activity.ActivityKey
import com.xiaocydx.performance.runtime.activity.ActivityWatcher
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.CPUSampler
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.sample.StackSampler
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.looper.CompositeLooperCallback
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.LooperWatcher
import com.xiaocydx.performance.runtime.looper.Start
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * @author xcc
 * @date 2025/3/19
 */
object Performance {
    private val host = HostImpl()
    private var isInitialized = false

    @MainThread
    fun init(application: Application, config: Config) {
        assertMainThread()
        if (isInitialized) return
        isInitialized = true
        config.checkProperty()

        host.init(application)
        ReferenceQueueDaemon().start()

        IdleHandlerAnalyzer(host).start()
        config.frameConfig?.let { FrameMetricsAnalyzer.create(host, it).start() }
        config.blockConfig?.let { BlockMetricsAnalyzer(host, it).start() }
        config.anrConfig?.let { ANRMetricsAnalyzer(host, it).start() }
        LooperWatcher.init(host, callback = host.getCallback())
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }
        private val defaultThread by lazy { HandlerThread("PerformanceDefaultThread").apply { start() } }

        private val callbacks = CompositeLooperCallback()
        private val activityWatcher = ActivityWatcher()
        private val analyzers = mutableListOf<Analyzer>()
        private lateinit var application: Application

        @Volatile
        private var sampleThread: HandlerThread? = null
        private lateinit var cpuSampler: CPUSampler
        private lateinit var stackSampler: StackSampler

        override val dumpLooper by lazy { dumpThread.looper!! }
        override val defaultLooper by lazy { defaultThread.looper!! }
        override val ams by lazy { application.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
        override val activityEvent get() = activityWatcher.event
        override val isRecordEnabled get() = History.isRecordEnabled

        fun init(application: Application) {
            this.application = application
            activityWatcher.init(application)
        }

        fun getCallback(): LooperCallback {
            callbacks.immutable()
            return callbacks
        }

        override fun createMainScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob(parentJob) + Dispatchers.Main.immediate)
        }

        override fun getActivity(key: ActivityKey): Activity? {
            return activityWatcher.getActivity(key)
        }

        override fun getLatestActivity(): Activity? {
            return activityWatcher.getLatestActivity()
        }

        override fun addCallback(callback: LooperCallback) {
            callbacks.add(callback)
        }

        override fun removeCallback(callback: LooperCallback) {
            callbacks.remove(callback)
        }

        override fun registerHistory(analyzer: Analyzer) {
            if (analyzers.contains(analyzer)) return
            val beforeEmpty = analyzers.isEmpty()
            analyzers.add(analyzer)

            History.init()
            var sampleThread = sampleThread
            if (sampleThread == null) {
                sampleThread = HandlerThread("PerformanceSampleThread")
                sampleThread.start()
                val intervalMillis = 500L
                cpuSampler = requireNotNull(
                    value = History.cpuSampler(sampleThread.looper, intervalMillis),
                    lazyMessage = { "History.init() failure" }
                )
                stackSampler = requireNotNull(
                    value = History.stackSampler(sampleThread.looper, intervalMillis),
                    lazyMessage = { "History.init() failure" }
                )
                // volatile write: release (Safe Publication)
                this.sampleThread = sampleThread
            }

            if (beforeEmpty) {
                callbacks.setFirst { current ->
                    when (current) {
                        is Start -> {
                            cpuSampler.start(current.uptimeMillis)
                            stackSampler.start(current.uptimeMillis)
                        }
                        is End -> {
                            cpuSampler.stop(current.uptimeMillis)
                            stackSampler.stop(current.uptimeMillis)
                        }
                    }
                }
            }
        }

        override fun unregisterHistory(analyzer: Analyzer) {
            if (analyzers.remove(analyzer) && analyzers.isEmpty()) {
                // TODO: 2025/4/26 cancel History.init()
                val uptimeMillis = SystemClock.uptimeMillis()
                callbacks.setFirst(callback = null)
                cpuSampler.stop(uptimeMillis)
                stackSampler.stop(uptimeMillis)
            }
        }

        override fun merger(idleThresholdMillis: Long, mergeThresholdMillis: Long): Merger {
            return requireNotNull(
                value = History.merger(idleThresholdMillis, mergeThresholdMillis),
                lazyMessage = { "History.init() failure" }
            )
        }

        override fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample> {
            if (sampleThread != null) {
                // volatile read: acquire (Safe Publication)
                return stackSampler.sampleList(startUptimeMillis, endUptimeMillis)
            }
            return emptyList()
        }

        override fun snapshot(startMark: Long, endMark: Long): Snapshot {
            return History.snapshot(startMark, endMark)
        }
    }

    data class Config(
        val frameConfig: FrameMetricsConfig? = null,
        val blockConfig: BlockMetricsConfig? = null,
        val anrConfig: ANRMetricsConfig? = null
    ) {

        internal fun checkProperty() {
            frameConfig?.checkProperty()
            blockConfig?.checkProperty()
            anrConfig?.checkProperty()
        }
    }
}