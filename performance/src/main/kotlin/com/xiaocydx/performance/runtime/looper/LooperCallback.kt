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
import android.os.SystemClock
import android.view.MotionEvent
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread

/**
 * 主线程[Looper]的处理回调
 *
 * @author xcc
 * @date 2025/3/27
 */
internal interface LooperCallback {

    fun dispatch(current: DispatchContext)
}

internal interface DispatchContext {

    /**
     * `true`-开始处理，`false`-结束处理
     */
    val isStart: Boolean

    /**
     * 处理的场景
     */
    val scene: Scene

    /**
     * [Scene.Message]: Android 10以下 - Printer字符串，Android 10及以上 - [Message]。
     * [Scene.IdleHandler]：[IdleHandler]。
     * [Scene.NativeTouch]：[MotionEvent]。
     */
    val metadata: Any?

    /**
     * [LooperCallback.dispatch]的[SystemClock.uptimeMillis]
     */
    val uptimeMillis: Long

    /**
     * [LooperCallback.dispatch]的[SystemClock.currentThreadTimeMillis]
     */
    val threadTimeMillis: Long
}

internal enum class Scene {
    Message, IdleHandler, NativeTouch
}

@MainThread
internal class CompositeLooperCallback : LooperCallback {
    private var callbacks = mutableListOf<LooperCallback>()
    private var dispatchingCallbacks = emptyList<LooperCallback>()
    private var isImmutable = false

    fun immutable() {
        isImmutable = true
    }

    fun add(callback: LooperCallback) {
        assertMainThread()
        if (isImmutable) {
            callbacks = (callbacks + callback).toMutableList()
        } else {
            callbacks.add(callback)
        }
    }

    fun remove(callback: LooperCallback) {
        assertMainThread()
        if (isImmutable) {
            callbacks = (callbacks - callback).toMutableList()
        } else {
            callbacks.remove(callback)
        }
    }

    override fun dispatch(current: DispatchContext) {
        if (current.isStart) {
            dispatchingCallbacks = callbacks
            dispatchCallbacks { it.dispatch(current) }
        } else {
            dispatchCallbacks { it.dispatch(current) }
            dispatchingCallbacks = emptyList()
        }
    }

    private inline fun dispatchCallbacks(action: (LooperCallback) -> Unit) {
        val callbacks = dispatchingCallbacks
        for (i in 0 until callbacks.size) action(callbacks[i])
    }
}