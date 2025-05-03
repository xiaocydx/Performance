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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.xiaocydx.performance

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity.ACTIVITY_SERVICE
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.analyzer.SampleConfig
import com.xiaocydx.performance.analyzer.anr.ANRMetricsAnalyzer
import com.xiaocydx.performance.analyzer.anr.ANRMetricsConfig
import com.xiaocydx.performance.analyzer.block.BlockMetricsAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.stable.IdleHandlerAnalyzer
import com.xiaocydx.performance.runtime.component.ActivityEvent
import com.xiaocydx.performance.runtime.component.ActivityKey
import com.xiaocydx.performance.runtime.component.ComponentWatcher
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.sample.Sampler
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.looper.CompositeLooperCallback
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.LooperDispatcher
import com.xiaocydx.performance.runtime.looper.LooperIdleHandlerWatcher
import com.xiaocydx.performance.runtime.looper.LooperMessageWatcherApi
import com.xiaocydx.performance.runtime.looper.LooperMessageWatcherApi29
import com.xiaocydx.performance.runtime.looper.LooperNativeInputWatcher
import com.xiaocydx.performance.runtime.looper.LooperWatcher
import com.xiaocydx.performance.runtime.looper.Start
import com.xiaocydx.performance.runtime.signal.Signal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

        host.init(application, config)
        ReferenceQueueDaemon().start()

        IdleHandlerAnalyzer(host).start()
        config.frameConfig.takeIf { it.receivers.isNotEmpty() }
            ?.let { FrameMetricsAnalyzer.create(host, it).start() }
        config.blockConfig.takeIf { it.receivers.isNotEmpty() }
            ?.let { BlockMetricsAnalyzer(host, it).start() }
        config.anrConfig.takeIf { it.receivers.isNotEmpty() }
            ?.let { ANRMetricsAnalyzer(host, it, config.blockConfig).start() }
        setupLooperWatcher()
    }

    private fun setupLooperWatcher() {
        val mainQueue = Looper.myQueue()
        val mainLooper = Looper.getMainLooper()
        val dispatcher = LooperDispatcher(host.getCallback())
        val scope = host.createMainScope()
        scope.launch {
            while (true) {
                val watcher = if (Build.VERSION.SDK_INT < 29) {
                    LooperMessageWatcherApi.setup(mainLooper, dispatcher)
                } else {
                    runCatching { LooperMessageWatcherApi29.setupOrThrow(mainLooper, dispatcher) }
                        .getOrNull() ?: LooperMessageWatcherApi.setup(mainLooper, dispatcher)
                }
                watcher.awaitGC()
            }
        }

        scope.launch {
            while (true) {
                val watcher = LooperIdleHandlerWatcher.setup(mainQueue, dispatcher)
                watcher.awaitGC()
            }
        }

        scope.launch {
            host.activityEvent.filterIsInstance<ActivityEvent.Created>().collect {
                val activity = host.getActivity(it.activityKey) ?: return@collect
                LooperNativeInputWatcher.setup(activity.window, dispatcher)
            }
        }
    }

    private suspend fun LooperWatcher.awaitGC() {
        suspendCancellableCoroutine { cont -> trackGC { cont.resume(Unit) } }
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }
        private val defaultThread by lazy { HandlerThread("PerformanceDefaultThread").apply { start() } }

        private val component = ComponentWatcher()
        private val callbacks = CompositeLooperCallback()
        private val analyzers = mutableListOf<Analyzer>()
        private lateinit var application: Application
        private lateinit var config: Config

        @Volatile
        private var sampleThread: HandlerThread? = null
        private lateinit var sampler: Sampler

        override val pid by lazy { Process.myPid() }
        override val dumpLooper by lazy { dumpThread.looper!! }
        override val defaultLooper by lazy { defaultThread.looper!! }
        override val ams by lazy { application.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
        override val anrEvent by lazy { MutableSharedFlow<Unit>(extraBufferCapacity = Int.MAX_VALUE) }
        override val activityEvent = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = Int.MAX_VALUE)
        override val isRecordEnabled get() = History.isRecordEnabled

        fun init(application: Application, config: Config) {
            this.application = application
            this.config = config
            component.init(application, send = activityEvent::tryEmit)
        }

        fun getCallback(): LooperCallback {
            callbacks.immutable()
            return callbacks
        }

        override fun createMainScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob(parentJob) + Dispatchers.Main.immediate)
        }

        override fun getActivity(key: ActivityKey): Activity? {
            return component.getActivity(key)
        }

        override fun getLatestActivity(): Activity? {
            return component.getLatestActivity()
        }

        override fun getActiveActivityCount(): Int {
            return component.getActiveActivityCount()
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
                sampleThread = HandlerThread("PerformanceSampleThread", THREAD_PRIORITY_BACKGROUND)
                sampleThread.start()
                sampler = requireHistory(History.sampler(
                    looper = sampleThread.looper,
                    intervalMillis = config.sampleConfig.intervalMillis
                ))
                // volatile write: release (Safe Publication)
                this.sampleThread = sampleThread
            }

            if (beforeEmpty) {
                callbacks.setFirst { current ->
                    when (current) {
                        is Start -> sampler.start(current.uptimeMillis)
                        is End -> sampler.stop(current.uptimeMillis)
                    }
                }
            }
            if (analyzer is ANRMetricsAnalyzer) {
                Signal.setANRCallback { anrEvent.tryEmit(Unit) }
            }
        }

        override fun unregisterHistory(analyzer: Analyzer) {
            if (!analyzers.remove(analyzer)) return
            // TODO: 2025/4/26 cancel History.init()
            if (analyzers.isEmpty()) {
                val uptimeMillis = SystemClock.uptimeMillis()
                callbacks.setFirst(callback = null)
                sampler.stop(uptimeMillis)
            }
            if (analyzer is ANRMetricsAnalyzer) {
                Signal.setANRCallback(null)
            }
        }

        override fun merger(idleThresholdMillis: Long, mergeThresholdMillis: Long): Merger {
            return requireHistory(History.merger(idleThresholdMillis, mergeThresholdMillis))
        }

        override fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample> {
            if (sampleThread != null) {
                // volatile read: acquire (Safe Publication)
                return sampler.sampleList(startUptimeMillis, endUptimeMillis)
            }
            return emptyList()
        }

        override fun sampleImmediately(): Sample? {
            if (sampleThread != null) {
                // volatile read: acquire (Safe Publication)
                return sampler.sampleImmediately()
            }
            return null
        }

        override fun snapshot(startMark: Long, endMark: Long): Snapshot {
            return History.snapshot(startMark, endMark)
        }

        private fun <T : Any> requireHistory(value: T?): T {
            return requireNotNull(value) { "History.init() failure" }
        }
    }

    data class Config(
        val sampleConfig: SampleConfig = SampleConfig(),
        val frameConfig: FrameMetricsConfig = FrameMetricsConfig(),
        val blockConfig: BlockMetricsConfig = BlockMetricsConfig(),
        val anrConfig: ANRMetricsConfig = ANRMetricsConfig()
    ) {

        internal fun checkProperty() {
            sampleConfig.checkProperty()
            frameConfig.checkProperty()
            blockConfig.checkProperty()
            anrConfig.checkProperty()
        }
    }
}