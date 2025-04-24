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

package com.xiaocydx.performance.runtime.looper

import android.os.Build
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.log
import com.xiaocydx.performance.runtime.activity.ActivityEvent
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 主线程[Looper]观察者
 *
 * @author xcc
 * @date 2025/3/27
 */
internal sealed class LooperWatcher {

    /**
     * 观察者被GC后，调用[thunk]
     */
    @MainThread
    abstract fun trackGC(thunk: Runnable)

    /**
     * 移除观察者
     */
    @MainThread
    @VisibleForTesting
    abstract fun remove()

    companion object {

        @MainThread
        fun init(
            host: Performance.Host,
            callback: LooperCallback,
            resetAfterGC: Boolean = true
        ) {
            val mainQueue = Looper.myQueue()
            val mainLooper = Looper.getMainLooper()
            val dispatcher = LooperDispatcher(callback)
            val scope = host.createMainScope()
            scope.launch {
                log { "设置MainLooperMessageWatcher" }
                while (true) {
                    val watcher = if (Build.VERSION.SDK_INT < 29) {
                        LooperMessageWatcherApi.setup(mainLooper, dispatcher)
                    } else {
                        runCatching { LooperMessageWatcherApi29.setupOrThrow(mainLooper, dispatcher) }
                            .getOrNull() ?: LooperMessageWatcherApi.setup(mainLooper, dispatcher)
                    }
                    if (resetAfterGC) watcher.awaitGC() else break
                    log { "重新设置MainLooperMessageWatcher" }
                }
            }

            scope.launch {
                log { "设置MainLooperIdleHandlerWatcher" }
                while (true) {
                    val watcher = LooperIdleHandlerWatcher.setup(mainQueue, dispatcher)
                    if (resetAfterGC) watcher.awaitGC() else break
                    log { "重新设置MainLooperIdleHandlerWatcher" }
                }
            }

            scope.launch {
                log { "设置MainLooperNativeTouchWatcher" }
                host.activityEvent.filterIsInstance<ActivityEvent.Created>().collect {
                    val activity = host.getActivity(it.activityKey) ?: return@collect
                    LooperNativeTouchWatcher.setup(activity.window, dispatcher)
                }
            }
        }
    }
}

private suspend fun LooperWatcher.awaitGC() {
    suspendCancellableCoroutine { cont -> trackGC { cont.resume(Unit) } }
}