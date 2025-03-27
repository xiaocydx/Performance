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
import android.os.Looper
import androidx.annotation.MainThread
import com.xiaocydx.performance.monitor.ActivityResumedIdleMonitor
import com.xiaocydx.performance.monitor.MainLooperANRMonitor
import com.xiaocydx.performance.monitor.MainLooperBlockMonitor
import com.xiaocydx.performance.reference.Cleaner
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
        ActivityResumedIdleMonitor(host).init()

        val callback = CompositeMainLooperCallback()
        callback.add(MainLooperANRMonitor().init())
        callback.add(MainLooperBlockMonitor().init())
        MainLooperWatcher.init(host, callback)
    }

    private class HostImpl : Host {
        private val parentJob = SupervisorJob()

        override val mainLooper = Looper.getMainLooper()!!

        override val mainDispatcher: MainCoroutineDispatcher
            get() = Dispatchers.Main.immediate

        override val activityEvent: SharedFlow<ActivityEvent>
            get() = activityWatcher.event

        override fun createMainScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob(parentJob) + mainDispatcher)
        }

        override fun getActivity(hashCode: Int): Activity? {
            return activityWatcher.getActivity(hashCode)
        }
    }

    private class ReferenceQueueDaemon : Runnable {

        override fun run() {
            while (true) {
                val reference = Cleaner.queue.remove() as Cleaner
                reference.clean()
            }
        }

        fun start() {
            val thread = Thread(this, "PerformanceReferenceQueueDaemon")
            thread.isDaemon = true
            thread.start()
        }
    }

    internal interface Host {
        val mainLooper: Looper

        val mainDispatcher: MainCoroutineDispatcher

        val activityEvent: SharedFlow<ActivityEvent>

        fun createMainScope(): CoroutineScope

        @MainThread
        fun getActivity(hashCode: Int): Activity?
    }
}