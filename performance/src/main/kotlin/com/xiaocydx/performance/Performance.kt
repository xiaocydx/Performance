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
import com.xiaocydx.performance.analyzer.stable.ActivityResumedIdleAnalyzer
import com.xiaocydx.performance.analyzer.anr.ANRAnalyzer
import com.xiaocydx.performance.analyzer.jank.JankAnalyzer
import com.xiaocydx.performance.gc.ReferenceQueueDaemon
import com.xiaocydx.performance.watcher.activity.ActivityEvent
import com.xiaocydx.performance.watcher.activity.ActivityWatcher
import com.xiaocydx.performance.watcher.looper.CompositeMainLooperCallback
import com.xiaocydx.performance.watcher.looper.MainLooperWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
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
    fun init(application: Application) {
        assertMainThread()
        if (isInitialized) return
        isInitialized = true
        ReferenceQueueDaemon().start()

        activityWatcher.init(application)
        ActivityResumedIdleAnalyzer(host).init()

        val callback = CompositeMainLooperCallback()
        callback.add(ANRAnalyzer().init())
        callback.add(JankAnalyzer(host).init(threshold = 300L))
        MainLooperWatcher.init(host, callback)
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()
        private val dumpThread by lazy { HandlerThread("PerformanceDumpThread").apply { start() } }

        override val mainLooper = Looper.getMainLooper()!!

        override val dumpLooper by lazy { dumpThread.looper!! }

        override val mainDispatcher: MainCoroutineDispatcher
            get() = Dispatchers.Main.immediate

        override val activityEvent: SharedFlow<ActivityEvent>
            get() = activityWatcher.event

        override fun createMainScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob(parentJob) + mainDispatcher)
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

        val mainDispatcher: MainCoroutineDispatcher

        val activityEvent: SharedFlow<ActivityEvent>

        fun createMainScope(): CoroutineScope

        @MainThread
        fun getActivity(key: Int): Activity?

        fun getLastActivity(): Activity?
    }
}