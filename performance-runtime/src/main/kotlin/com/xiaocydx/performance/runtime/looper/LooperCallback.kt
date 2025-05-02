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
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.history.History

/**
 * 主线程[Looper]的处理回调
 *
 * @author xcc
 * @date 2025/3/27
 */
internal fun interface LooperCallback {

    fun dispatch(current: DispatchContext)
}

internal sealed interface DispatchContext {

    val scene: Scene

    val uptimeMillis: Long

    fun isFrom(source: Source): Boolean
}

internal interface Start : DispatchContext {

    /**
     * [History.startMark]
     */
    val mark: Long

    /**
     * [scene]的元数据：
     * * [Scene.Message]: Android 10以下 - Printer字符串，Android 10及以上 - `null`。
     * * [Scene.IdleHandler]：[IdleHandler]。
     * * [Scene.NativeInput]：[MotionEvent]或[KeyEvent]。
     */
    val metadata: Metadata

    val threadTimeMillis: Long
}

internal interface End : DispatchContext {

    /**
     * [History.endMark]
     */
    val mark: Long

    /**
     * [scene]的元数据：
     * * [Scene.Message]: Android 10以下 - Printer字符串，Android 10及以上 - [Message]。
     * * [Scene.IdleHandler]：[IdleHandler]。
     * * [Scene.NativeInput]：[MotionEvent]或[KeyEvent]。
     */
    val metadata: Metadata
}

internal enum class Scene {
    Message, IdleHandler, NativeInput
}

internal enum class Source {
    ActivityThread, // Choreographer, ViewRootImpl
}

@MainThread
internal class CompositeLooperCallback : LooperCallback {
    private var first: LooperCallback? = null
    private var callbacks = mutableListOf<LooperCallback>()
    private var dispatchingFirst: LooperCallback? = null
    private var dispatchingCallbacks = emptyList<LooperCallback>()
    private var isImmutable = false

    fun immutable() {
        isImmutable = true
    }

    fun setFirst(callback: LooperCallback?) {
        first = callback
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
        when (current) {
            is Start -> {
                dispatchingFirst = first
                dispatchingCallbacks = callbacks
                dispatchCallbacks { it.dispatch(current) }
            }
            is End -> {
                dispatchCallbacks { it.dispatch(current) }
                dispatchingCallbacks = emptyList()
                dispatchingFirst = null
            }
        }
    }

    private inline fun dispatchCallbacks(action: (LooperCallback) -> Unit) {
        dispatchingFirst?.apply(action)
        for (i in 0 until dispatchingCallbacks.size) action(callbacks[i])
    }
}