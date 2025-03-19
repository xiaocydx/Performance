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
import com.xiaocydx.performance.log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xcc
 * @date 2025/3/19
 */
internal object MainLooperMonitor {
    private val isInitialized = AtomicBoolean(false)

    @AnyThread
    fun init() {
        if (!isInitialized.compareAndSet(false, true)) return
        log { "初始化${MainLooperMonitor::class.java.simpleName}" }
        runOnMainThread {
            setupIdleAnalyzer(fromInit = true)
            setupMessageAnalyzer(fromInit = true)
        }
    }

    private fun setupIdleAnalyzer(fromInit: Boolean) {
        val reason = if (fromInit) "设置" else "重新设置"
        log { "${reason}${MainLooperIdleAnalyzer::class.java.simpleName}" }
        runOnMainThread {
            MainLooperIdleAnalyzer.setup().trackGC { setupIdleAnalyzer(fromInit = false) }
        }
    }

    private fun setupMessageAnalyzer(fromInit: Boolean) {
        val reason = if (fromInit) "设置" else "重新设置"
        log { "${reason}${MainLooperMessageAnalyzer::class.java.simpleName}" }
        runOnMainThread {
            MainLooperMessageAnalyzer.setup().trackGC { setupMessageAnalyzer(fromInit = false) }
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