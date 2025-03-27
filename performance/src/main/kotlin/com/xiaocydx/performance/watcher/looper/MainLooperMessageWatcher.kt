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

package com.xiaocydx.performance.watcher.looper

import android.os.Looper
import android.os.Message
import android.util.Printer
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.xiaocydx.performance.Reflection
import com.xiaocydx.performance.fake.FakeLooperObserver
import com.xiaocydx.performance.fake.toReal
import com.xiaocydx.performance.reference.Cleaner
import com.xiaocydx.performance.watcher.looper.MainLooperCallback.Type

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class MainLooperMessageWatcher private constructor(
    private val original: Printer?,
    private val mainLooper: Looper,
    private val callback: MainLooperCallback
) : MainLooperWatcher() {
    private var isStarted = false
    private val printer = PrinterImpl()

    override fun trackGC(thunk: Runnable) {
        Cleaner.add(printer, thunk)
    }

    override fun remove() {
        mainLooper.setMessageLogging(null)
    }

    private inner class PrinterImpl : Printer {

        @MainThread
        override fun println(x: String?) {
            original?.println(x)
            isStarted = !isStarted
            if (isStarted) {
                callback.start(msg = null, type = Type.Message)
            } else {
                callback.end(msg = null, type = Type.Message)
            }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setup(
            mainLooper: Looper,
            callback: MainLooperCallback
        ): MainLooperWatcher {
            val original = runCatching {
                val fields = Looper::class.java.toSafe().declaredInstanceFields
                val mLogging = fields.find("mLogging").apply { isAccessible = true }
                mLogging.get(mainLooper) as? Printer
            }.getOrNull()
            val watcher = MainLooperMessageWatcher(original, mainLooper, callback)
            mainLooper.setMessageLogging(watcher.printer)
            return watcher
        }
    }
}

@RequiresApi(29)
internal class MainLooperMessageWatcher29 private constructor(
    original: Any?,
    private val mainLooper: Looper,
    private val callback: MainLooperCallback
) : MainLooperWatcher() {
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
            ifMainThread { callback.start(msg = null, type = Type.Message) }
        }

        @AnyThread
        override fun messageDispatched(msg: Message) {
            ifMainThread { callback.end(msg = msg, type = Type.Message) }
        }

        @AnyThread
        override fun dispatchingThrewException(msg: Message, exception: Exception) {
            ifMainThread { callback.end(msg = msg, type = Type.Message) }
        }
    }

    companion object : Reflection {

        @MainThread
        fun setupOrThrow(
            mainLooper: Looper,
            callback: MainLooperCallback
        ): MainLooperWatcher {
            val fields = Looper::class.java.toSafe().declaredStaticFields
            val methods = Looper::class.java.toSafe().declaredMethods
            val sObserver = fields.find("sObserver").apply { isAccessible = true }
            val setObserver = methods.find("setObserver").apply { isAccessible = true }
            val original = sObserver.get(null)
            val watcher = MainLooperMessageWatcher29(original, mainLooper, callback)
            setObserver.invoke(null, watcher.realObserver)
            return watcher
        }
    }
}