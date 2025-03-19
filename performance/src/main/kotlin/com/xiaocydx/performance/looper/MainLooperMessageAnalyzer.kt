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

import android.os.Build
import android.os.Looper
import android.os.Message
import android.util.Printer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.xiaocydx.performance.Reflection
import com.xiaocydx.performance.fake.FakeLooperObserver
import com.xiaocydx.performance.fake.toReal
import com.xiaocydx.performance.log
import com.xiaocydx.performance.reference.Cleaner

/**
 * @author xcc
 * @date 2025/3/19
 */
internal sealed class MainLooperMessageAnalyzer {
    private var startTime = 0L

    @MainThread
    abstract fun trackGC(thunk: Runnable)

    @MainThread
    @VisibleForTesting
    abstract fun remove()

    @MainThread
    fun start(msg: Message?) {
        log { "MainLooperMessageAnalyzer start" }
        startTime = System.currentTimeMillis()
    }

    @MainThread
    fun end(msg: Message?) {
        log { "MainLooperMessageAnalyzer end, ${System.currentTimeMillis() - startTime}ms, msg = $msg" }
    }

    companion object {

        @MainThread
        fun setup() = if (Build.VERSION.SDK_INT < 29) {
            MainLooperMessageAnalyzerImpl.setup()
        } else {
            runCatching { MainLooperMessageAnalyzerImpl29.setupOrThrow() }
                .getOrNull() ?: MainLooperMessageAnalyzerImpl.setup()
        }
    }
}

private class MainLooperMessageAnalyzerImpl private constructor(
    private val original: Printer?
) : MainLooperMessageAnalyzer() {
    private var isStarted = false
    private val printer = PrinterImpl()

    override fun trackGC(thunk: Runnable) {
        Cleaner.add(printer, thunk)
    }

    override fun remove() {
        Looper.getMainLooper().setMessageLogging(null)
    }

    private inner class PrinterImpl : Printer {

        @MainThread
        override fun println(x: String?) {
            original?.println(x)
            isStarted = !isStarted
            if (isStarted) {
                start(msg = null)
            } else {
                end(msg = null)
            }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setup(): MainLooperMessageAnalyzer {
            val original = runCatching {
                val fields = Looper::class.java.toSafe().declaredInstanceFields
                val mLogging = fields.find("mLogging").apply { isAccessible = true }
                mLogging.get(Looper.getMainLooper()) as? Printer
            }.getOrNull()
            val analyzer = MainLooperMessageAnalyzerImpl(original)
            Looper.getMainLooper().setMessageLogging(analyzer.printer)
            return analyzer
        }
    }
}

@RequiresApi(29)
private class MainLooperMessageAnalyzerImpl29 private constructor(
    original: Any?
) : MainLooperMessageAnalyzer() {
    private val mainLooper = Looper.getMainLooper()
    private val fakeObserver = FakeLooperObserverImpl()
    private val realObserver = fakeObserver.toReal(original)

    override fun trackGC(thunk: Runnable) {
        Cleaner.add(realObserver, thunk)
    }

    override fun remove() {
        val fields = Looper::class.java.toSafe().declaredStaticFields
        val sObserver = fields.find("sObserver").apply { isAccessible = true }
        sObserver.isAccessible = true
        sObserver.set(null, null)
    }

    private inline fun ifMainThread(action: () -> Unit) {
        if (mainLooper.isCurrentThread) action()
    }

    private inner class FakeLooperObserverImpl : FakeLooperObserver {

        @AnyThread
        override fun messageDispatchStarting() {
            ifMainThread { start(msg = null) }
        }

        @AnyThread
        override fun messageDispatched(msg: Message) {
            ifMainThread { end(msg = msg) }
        }

        @AnyThread
        override fun dispatchingThrewException(msg: Message, exception: Exception) {
            ifMainThread { end(msg = msg) }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setupOrThrow(): MainLooperMessageAnalyzer {
            val fields = Looper::class.java.toSafe().declaredStaticFields
            val methods = Looper::class.java.toSafe().declaredMethods
            val sObserver = fields.find("sObserver").apply { isAccessible = true }
            val setObserver = methods.find("setObserver").apply { isAccessible = true }

            val original = sObserver.get(null)
            val analyzer = MainLooperMessageAnalyzerImpl29(original)
            setObserver.invoke(null, analyzer.realObserver)
            return analyzer
        }
    }
}