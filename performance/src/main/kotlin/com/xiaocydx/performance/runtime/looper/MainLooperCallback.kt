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

@file:Suppress("ReplaceManualRangeWithIndicesCalls")

package com.xiaocydx.performance.runtime.looper

import android.os.Looper
import android.os.Message
import android.os.MessageQueue.IdleHandler
import android.view.MotionEvent
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type

/**
 * 主线程[Looper]的处理回调
 *
 * @author xcc
 * @date 2025/3/27
 */
internal interface MainLooperCallback {

    /**
     * 开始处理[type]
     *
     * @param data
     * * [Type.Message]: Android 10以下 - Printer字符串，Android 10及以上 - `null`。
     * * [Type.IdleHandler]：[IdleHandler]。
     * * [Type.NativeTouch]：[MotionEvent]。
     */
    @MainThread
    fun start(type: Type, data: Any?)

    /**
     * 结束处理[type]
     *
     * @param data
     * * [Type.Message]: Android 10以下 - Printer字符串，Android 10及以上 - [Message]。
     * * [Type.IdleHandler]：[IdleHandler]。
     * * [Type.NativeTouch]：[MotionEvent]。
     */
    @MainThread
    fun end(type: Type, data: Any?)

    enum class Type {
        Message, IdleHandler, NativeTouch
    }
}

@MainThread
internal class CompositeMainLooperCallback : MainLooperCallback {
    private var callbacks = mutableListOf<MainLooperCallback>()
    private var dispatchingCallbacks = emptyList<MainLooperCallback>()
    private var isImmutable = false

    fun immutable() {
        isImmutable = true
    }

    fun add(callback: MainLooperCallback) {
        assertMainThread()
        if (isImmutable) {
            callbacks = (callbacks + callback).toMutableList()
        } else {
            callbacks.add(callback)
        }
    }

    fun remove(callback: MainLooperCallback) {
        assertMainThread()
        if (isImmutable) {
            callbacks = (callbacks - callback).toMutableList()
        } else {
            callbacks.remove(callback)
        }
    }

    override fun start(type: Type, data: Any?) {
        dispatchingCallbacks = callbacks
        dispatchCallbacks { it.start(type, data) }
    }

    override fun end(type: Type, data: Any?) {
        dispatchCallbacks { it.end(type, data) }
        dispatchingCallbacks = emptyList()
    }

    private inline fun dispatchCallbacks(action: (MainLooperCallback) -> Unit) {
        val callbacks = dispatchingCallbacks
        for (i in 0 until callbacks.size) action(callbacks[i])
    }
}

internal class NotReentrantMainLooperCallback(
    private val delegate: MainLooperCallback,
) : MainLooperCallback {
    private var current: Type? = null

    override fun start(type: Type, data: Any?) {
        if (current != null) return
        current = type
        delegate.start(type, data)
    }

    override fun end(type: Type, data: Any?) {
        if (current != type) return
        current = null
        delegate.end(type, data)
    }
}