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
import androidx.annotation.MainThread

/**
 * 主线程[Looper]的处理回调
 *
 * @author xcc
 * @date 2025/3/27
 */
internal interface MainLooperCallback {

    /**
     * 开始处理[type]
     */
    @MainThread
    fun start(msg: Message?, type: Type)

    /**
     * 结束处理[type]
     *
     * @param msg 当[Type]为[Type.Message]，且是高版本实现时，才不为`null`
     */
    @MainThread
    fun end(msg: Message?, type: Type)

    enum class Type {
        Message, IdleHandler
    }
}

internal class CompositeMainLooperCallback : MainLooperCallback {
    private val callbacks = ArrayList<MainLooperCallback>()

    fun add(callback: MainLooperCallback) {
        callbacks.add(callback)
    }

    override fun start(msg: Message?, type: MainLooperCallback.Type) {
        dispatchCallbacks { it.start(msg, type) }
    }

    override fun end(msg: Message?, type: MainLooperCallback.Type) {
        dispatchCallbacks { it.end(msg, type) }
    }

    private inline fun dispatchCallbacks(action: (MainLooperCallback) -> Unit) {
        for (i in 0 until callbacks.size) action(callbacks[i])
    }
}