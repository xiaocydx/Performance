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

package com.xiaocydx.performance.looper

import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.log
import com.xiaocydx.performance.reference.GCHelper
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xcc
 * @date 2025/3/19
 */
internal object MainLooperMonitor {
    private val isStarted = AtomicBoolean(false)
    private var messageAnalyzerRef: WeakReference<MessageAnalyzer>? = null

    @AnyThread
    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        runOnMainThread {
            // TODO: IdleHandler
            val messageAnalyzer = MessageAnalyzer.setup()
            messageAnalyzer.trackGC(::restartAfterGC)
            messageAnalyzerRef = WeakReference(messageAnalyzer)
        }
    }

    @AnyThread
    private fun restartAfterGC() {
        if (!isStarted.compareAndSet(true, false)) return
        log { "重新启动${MainLooperMonitor::class.java.simpleName}" }
        start()
    }

    @VisibleForTesting
    fun gcThenRestart() {
        runOnMainThread {
            messageAnalyzerRef?.get()?.remove()
            Handler(Looper.getMainLooper()).post { GCHelper.runGC() }
        }
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.getMainLooper().isCurrentThread) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post { action() }
        }
    }
}