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
                        LooperMessageWatcher.setup(mainLooper, dispatcher)
                    } else {
                        runCatching { LooperMessageWatcher29.setupOrThrow(mainLooper, dispatcher) }
                            .getOrNull() ?: LooperMessageWatcher.setup(mainLooper, dispatcher)
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