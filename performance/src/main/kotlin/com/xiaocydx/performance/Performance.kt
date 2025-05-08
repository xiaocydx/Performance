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
import com.xiaocydx.performance.HistoryTokenStore.Count
import com.xiaocydx.performance.analyzer.anr.ANRMetricsAnalyzer
import com.xiaocydx.performance.analyzer.anr.ANRMetricsConfig
import com.xiaocydx.performance.analyzer.block.BlockMetricsAnalyzer
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.analyzer.frame.FrameMetricsAnalyzer
import com.xiaocydx.performance.analyzer.frame.FrameMetricsConfig
import com.xiaocydx.performance.analyzer.stable.IdleHandlerAnalyzer
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.component.ActivityEvent
import com.xiaocydx.performance.runtime.component.ActivityKey
import com.xiaocydx.performance.runtime.component.ComponentWatcher
import com.xiaocydx.performance.runtime.future.Future
import com.xiaocydx.performance.runtime.future.PendingMessage
import com.xiaocydx.performance.runtime.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.sample.Sampler
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.history.segment.Segment
import com.xiaocydx.performance.runtime.history.segment.collectFrom
import com.xiaocydx.performance.runtime.looper.CompositeLooperCallback
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.LooperDispatcher
import com.xiaocydx.performance.runtime.looper.LooperIdleHandlerWatcher
import com.xiaocydx.performance.runtime.looper.LooperMessageWatcherApi
import com.xiaocydx.performance.runtime.looper.LooperMessageWatcherApi29
import com.xiaocydx.performance.runtime.looper.LooperNativeInputWatcher
import com.xiaocydx.performance.runtime.looper.LooperWatcher
import com.xiaocydx.performance.runtime.looper.Source
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
    private val impl = mutableListOf<Any>()
    private var isInitialized = false

    @MainThread
    fun init(application: Application, config: Config) {
        assertMainThread()
        if (isInitialized) return
        isInitialized = true
        config.checkProperty()

        host.init(application, config)
        ReferenceQueueDaemon().start()

        val analyzers = listOf(
            IdleHandlerAnalyzer(host),
            config.frame.takeIf { it.receivers.isNotEmpty() }
                ?.let { FrameMetricsAnalyzer.create(host, it) },
            config.block.takeIf { it.receivers.isNotEmpty() }
                ?.let { BlockMetricsAnalyzer(host, it) },
            config.anr.takeIf { it.receivers.isNotEmpty() }
                ?.let { ANRMetricsAnalyzer(host, it, config.block) }
        )

        analyzers.forEach { it?.start() }
        synchronized(impl) { analyzers.forEach { it?.let(impl::add) } }
        setupLooperWatcher()
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> impl(clazz: Class<T>): T? {
        require(clazz.isInterface)
        return synchronized(impl) {
            impl.firstOrNull { clazz.isAssignableFrom(it.javaClass) } as? T
        }
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
        private val tokenStore = HistoryTokenStore()
        private lateinit var application: Application
        private lateinit var config: Config
        private lateinit var future: Future

        @Volatile
        private var sampleThread: HandlerThread? = null
        private var sampler: Sampler? = null

        private val segment = Segment()
        private var merger: Merger? = null

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
            future = Future(Looper.myQueue())
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

        override fun registerHistory(token: HistoryToken) {
            if (!tokenStore.add(token)) return

            if (tokenStore.isEmptyToNotEmpty(Count::total)) {
                History.init()
                callbacks.setFirst { current ->
                    when (current) {
                        is Start -> {
                            sampler?.start(current.uptimeMillis)
                            // collectFrom() time << 1ms
                            merger?.let { segment.collectFrom(current) }
                        }
                        is End -> {
                            sampler?.stop(current.uptimeMillis)
                            merger?.let {
                                segment.collectFrom(current)
                                if (segment.wallDurationMillis > config.block.blockThresholdMillis) {
                                    segment.isSingle = true
                                    segment.needRecord = true
                                    segment.needSample = true
                                    segment.endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                                } else if (current.isFrom(Source.ActivityThread)) {
                                    segment.isSingle = true
                                    segment.endThreadTimeMillis = SystemClock.currentThreadTimeMillis()
                                }
                                it.consume(segment)
                            }
                        }
                    }
                }
            }

            if (tokenStore.isEmptyToNotEmpty(Count::needSignal)) {
                Signal.setANRCallback { anrEvent.tryEmit(Unit) }
            }

            if (tokenStore.isEmptyToNotEmpty(Count::needSample)) {
                val sampleThread = HandlerThread("PerformanceSampleThread", THREAD_PRIORITY_BACKGROUND)
                sampleThread.start()
                sampler = requireHistory(History.sampler(
                    looper = sampleThread.looper,
                    intervalMillis = config.block.sampleIntervalMillis
                ))
                // volatile write: release (Safe Publication)
                this.sampleThread = sampleThread
            }

            if (tokenStore.isEmptyToNotEmpty(Count::needSegment)) {
                merger = requireHistory(History.merger(
                    idleThresholdMillis = config.anr.idleThresholdMillis,
                    mergeThresholdMillis = config.anr.mergeThresholdMillis
                ))
            }
        }

        override fun unregisterHistory(token: HistoryToken) {
            if (!tokenStore.remove(token)) return

            if (tokenStore.isNotEmptyToEmpty(Count::total)) {
                // TODO: 2025/4/26 cancel History.init()
                callbacks.setFirst(callback = null)
            }

            if (tokenStore.isNotEmptyToEmpty(Count::needSignal)) {
                Signal.setANRCallback(null)
            }

            if (tokenStore.isNotEmptyToEmpty(Count::needSample)) {
                sampleThread!!.quit()
                sampler!!.stop(SystemClock.uptimeMillis())
                sampler = null
                // volatile write: release (Safe Publication)
                sampleThread = null
            }

            if (tokenStore.isEmptyToNotEmpty(Count::needSegment)) {
                merger = null
            }
        }

        override fun snapshot(startMark: Long, endMark: Long): Snapshot {
            return History.snapshot(startMark, endMark)
        }

        override fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample> {
            if (sampleThread != null) {
                // volatile read: acquire (Safe Publication)
                return sampler!!.sampleList(startUptimeMillis, endUptimeMillis)
            }
            return emptyList()
        }

        override fun sampleImmediately(): Sample? {
            if (sampleThread != null) {
                // volatile read: acquire (Safe Publication)
                return sampler!!.sampleImmediately()
            }
            return null
        }

        override fun segmentRange(startUptimeMillis: Long, endUptimeMillis: Long): List<Merger.Range> {
            assertMainThread()
            return merger?.copy(startUptimeMillis, endUptimeMillis) ?: emptyList()
        }

        override fun getPendingList(uptimeMillis: Long): List<PendingMessage> {
            return future.getPendingList(uptimeMillis)
        }

        private fun <T : Any> requireHistory(value: T?): T {
            return requireNotNull(value) { "History.init() failure" }
        }
    }

    data class Config(
        val frame: FrameMetricsConfig = FrameMetricsConfig(),
        val block: BlockMetricsConfig = BlockMetricsConfig(),
        val anr: ANRMetricsConfig = ANRMetricsConfig()
    ) {

        internal fun checkProperty() {
            frame.checkProperty()
            block.checkProperty()
            anr.checkProperty()
        }
    }
}