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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package com.xiaocydx.performance.runtime.looper

import android.os.Looper
import android.os.Message
import android.util.Printer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.fake.FakeLooperObserver
import com.xiaocydx.performance.fake.toReal
import com.xiaocydx.performance.runtime.Reflection
import com.xiaocydx.performance.runtime.gc.Cleaner

internal class LooperMessageWatcherApi private constructor(
    private val original: Printer?,
    private val mainLooper: Looper,
    private val dispatcher: LooperDispatcher
) : LooperWatcher() {
    private val printer = PrinterImpl()

    override fun trackGC(thunk: Runnable) {
        Cleaner.add(printer, thunk)
    }

    override fun remove() {
        mainLooper.setMessageLogging(null)
    }

    private inner class PrinterImpl : Printer {

        @MainThread
        override fun println(x: String) {
            original?.println(x)
            when {
                x[0] == '>' -> dispatcher.start(scene = Scene.Message, metadata = x)
                x[0] == '<' -> dispatcher.end(scene = Scene.Message, metadata = x)
            }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setup(
            mainLooper: Looper,
            dispatcher: LooperDispatcher
        ): LooperWatcher {
            val original = runCatching {
                val fields = Looper::class.java.toSafe().declaredInstanceFields
                val mLogging = fields.find("mLogging").apply { isAccessible = true }
                mLogging.get(mainLooper) as? Printer
            }.getOrNull()
            val watcher = LooperMessageWatcherApi(original, mainLooper, dispatcher)
            mainLooper.setMessageLogging(watcher.printer)
            return watcher
        }
    }
}

@RequiresApi(29)
internal class LooperMessageWatcherApi29 private constructor(
    original: Any?,
    mainLooper: Looper,
    private val dispatcher: LooperDispatcher
) : LooperWatcher() {
    private val mainThreadId = mainLooper.thread.id
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
        if (Thread.currentThread().id == mainThreadId) action()
    }

    private inner class FakeLooperObserverImpl : FakeLooperObserver {

        @AnyThread
        override fun messageDispatchStarting() {
            ifMainThread { dispatcher.start(scene = Scene.Message, metadata = null) }
        }

        @AnyThread
        override fun messageDispatched(msg: Message) {
            ifMainThread { dispatcher.end(scene = Scene.Message, metadata = msg) }
        }

        @AnyThread
        override fun dispatchingThrewException(msg: Message, exception: Exception) {
            ifMainThread { dispatcher.end(scene = Scene.Message, metadata = msg) }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setupOrThrow(
            mainLooper: Looper,
            dispatcher: LooperDispatcher
        ): LooperWatcher {
            val fields = Looper::class.java.toSafe().declaredStaticFields
            val methods = Looper::class.java.toSafe().declaredMethods
            val sObserver = fields.find("sObserver").apply { isAccessible = true }
            val setObserver = methods.find("setObserver").apply { isAccessible = true }
            val original = sObserver.get(null)
            val watcher = LooperMessageWatcherApi29(original, mainLooper, dispatcher)
            setObserver.invoke(null, watcher.realObserver)
            return watcher
        }
    }
}