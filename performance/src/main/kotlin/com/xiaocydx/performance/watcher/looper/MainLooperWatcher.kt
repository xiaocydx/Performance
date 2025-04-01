package com.xiaocydx.performance.watcher.looper

import android.os.Build
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.log
import com.xiaocydx.performance.watcher.activity.ActivityEvent
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
internal sealed class MainLooperWatcher {

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
            callback: MainLooperCallback,
            resetAfterGC: Boolean = true
        ) {
            val mainLooper = host.mainLooper
            val finalCallback = NotReentrantMainLooperCallback(callback)
            val scope = host.createMainScope()
            scope.launch {
                log { "设置MainLooperMessageWatcher" }
                while (true) {
                    val watcher = if (Build.VERSION.SDK_INT < 29) {
                        MainLooperMessageWatcher.setup(mainLooper, finalCallback)
                    } else {
                        runCatching { MainLooperMessageWatcher29.setupOrThrow(mainLooper, finalCallback) }
                            .getOrNull() ?: MainLooperMessageWatcher.setup(mainLooper, finalCallback)
                    }
                    if (resetAfterGC) watcher.awaitGC() else break
                    log { "重新设置MainLooperMessageWatcher" }
                }
            }

            scope.launch {
                log { "设置MainLooperIdleHandlerWatcher" }
                while (true) {
                    val watcher = MainLooperIdleHandlerWatcher.setup(mainLooper, finalCallback)
                    if (resetAfterGC) watcher.awaitGC() else break
                    log { "重新设置MainLooperIdleHandlerWatcher" }
                }
            }

            scope.launch {
                log { "设置MainLooperNativeTouchWatcher" }
                host.activityEvent.filterIsInstance<ActivityEvent.Created>().collect {
                    val activity = host.getActivity(it.activityKey) ?: return@collect
                    MainLooperNativeTouchWatcher.setup(activity.window, finalCallback)
                }
            }
        }
    }
}

private suspend fun MainLooperWatcher.awaitGC() {
    suspendCancellableCoroutine { cont -> trackGC { cont.resume(Unit) } }
}