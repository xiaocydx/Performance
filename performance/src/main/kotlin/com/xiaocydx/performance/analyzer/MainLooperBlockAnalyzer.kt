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

package com.xiaocydx.performance.analyzer

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.watcher.looper.MainLooperCallback
import com.xiaocydx.performance.watcher.looper.MainLooperCallback.Type

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class MainLooperBlockAnalyzer(
    private val host: Performance.Host
) : MainLooperCallback {
    private val dumpRunner = DumpRunner()

    fun init(threshold: Long) = apply {
        require(threshold > 0)
        dumpRunner.setThreshold(threshold)
    }

    override fun start(type: Type, data: Any?) {
        dumpRunner.start()
    }

    override fun end(type: Type, data: Any?) {
        dumpRunner.end()
    }

    private inner class DumpRunner : Runnable {
        private var handler: Handler? = null
        private var threshold = 0L
        private var delayMillis = 0L

        init {
            setThreshold(1000L)
        }

        fun setThreshold(threshold: Long) {
            this.threshold = threshold
            delayMillis = (threshold * 0.7).toLong()
        }

        fun start() {
            getHandler().postDelayed(this, delayMillis)
        }

        fun end() {
            getHandler().removeCallbacks(this)
        }

        override fun run() {
            val stackTrace = Looper.getMainLooper().thread.stackTrace
            val cause = RuntimeException()
            cause.stackTrace = stackTrace
            Log.e("dump", "出现卡顿", cause)
        }

        private fun getHandler(): Handler {
            if (handler == null) {
                handler = Handler(host.dumpLooper)
            }
            return handler!!
        }
    }
}