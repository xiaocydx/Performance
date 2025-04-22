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

package com.xiaocydx.performance.runtime.looper

import android.annotation.SuppressLint
import android.os.Handler
import android.os.SystemClock
import com.xiaocydx.performance.runtime.Reflection

/**
 * @author xcc
 * @date 2025/4/21
 */
internal class LooperDispatcher(private val callback: LooperCallback) {
    private var dispatchingScene: Scene? = null
    private val current = DispatchContextImpl()

    fun start(scene: Scene, metadata: Any?) {
        if (dispatchingScene != null) return
        dispatchingScene = scene
        makeCurrent {
            isStart = true
            this.scene = scene
            this.metadata = metadata
        }
        callback.dispatch(current)
    }

    fun end(scene: Scene, metadata: Any?) {
        if (dispatchingScene != scene) return
        dispatchingScene = null
        makeCurrent {
            isStart = false
            this.scene = scene
            this.metadata = metadata
        }
        callback.dispatch(current)
        dispatchActivityThreadMessage = false
    }

    private inline fun makeCurrent(block: DispatchContextImpl.() -> Unit) {
        current.apply(block)
        current.uptimeMillis = SystemClock.uptimeMillis()
        current.threadTimeMillis = SystemClock.currentThreadTimeMillis()
    }

    private class DispatchContextImpl : DispatchContext {
        override var isStart = false
        override var scene = Scene.Message
        override var metadata: Any? = null
        override var uptimeMillis = 0L
        override var threadTimeMillis = 0L
        override val isFromActivityThread: Boolean
            get() = dispatchActivityThreadMessage
    }

    @SuppressLint("PrivateApi")
    private companion object : Reflection {
        /**
         * 反射替换的`callback`标记`true`，由[end]标识`false`，
         * 确保`callback`被其他逻辑反射替换了，也能重置属性值。
         */
        var dispatchActivityThreadMessage = false

        init {
            runCatching {
                val clazz = Class.forName("android.app.ActivityThread")
                val sHandlerField = clazz.toSafe().declaredStaticFields.find("sMainThreadHandler")
                sHandlerField.isAccessible = true
                val handler = (sHandlerField.get(null) as? Handler) ?: return@runCatching

                val mCallbackField = Handler::class.java.toSafe().declaredInstanceFields.find("mCallback")
                mCallbackField.isAccessible = true
                val original = mCallbackField.get(handler) as? Handler.Callback
                val callback = Handler.Callback { msg ->
                    // 当message.callback != null时，Handler.Callback不会触发
                    dispatchActivityThreadMessage = true
                    original?.handleMessage(msg) ?: false
                }
                mCallbackField.set(handler, callback)
            }
        }
    }
}
