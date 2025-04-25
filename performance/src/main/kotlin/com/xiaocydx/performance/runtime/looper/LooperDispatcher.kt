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
import android.os.Message
import android.os.MessageQueue.IdleHandler
import android.os.SystemClock
import android.view.MotionEvent
import com.xiaocydx.performance.runtime.Reflection
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.History.NO_MARK

/**
 * @author xcc
 * @date 2025/4/21
 */
internal class LooperDispatcher(private val callback: LooperCallback) {
    private val start = StartImpl()
    private val end = EndImpl()
    private var dispatchingScene: Scene? = null
    private var dispatchingMark = NO_MARK

    fun start(scene: Scene, metadata: Any?) {
        if (dispatchingScene != null) return
        dispatchingScene = scene
        start.mark = History.startMark()
        start.scene = scene
        start.uptimeMillis = SystemClock.uptimeMillis()
        start.threadTimeMillis = SystemClock.currentThreadTimeMillis()
        start.metadata.value = metadata
        dispatchingMark = start.mark
        callback.dispatch(start)
        start.metadata.value = null
    }

    fun end(scene: Scene, metadata: Any) {
        if (dispatchingScene != scene) return
        dispatchingScene = null
        end.mark = if (dispatchingMark > NO_MARK) History.endMark() else NO_MARK
        end.scene = scene
        end.uptimeMillis = SystemClock.uptimeMillis()
        end.metadata.value = metadata
        callback.dispatch(end)
        end.metadata.value = null
        dispatchActivityThreadMessage = false
    }

    private class StartImpl : Start {
        override var mark = NO_MARK
        override var scene = Scene.Message
        override var uptimeMillis = 0L
        override var threadTimeMillis = 0L
        override val metadata = MetadataImpl()
        override val isFromActivityThread: Boolean
            get() = dispatchActivityThreadMessage
    }

    private class EndImpl : End {
        override var mark = NO_MARK
        override var scene = Scene.Message
        override var uptimeMillis = 0L
        override val metadata = MetadataImpl()
        override val isFromActivityThread: Boolean
            get() = dispatchActivityThreadMessage
    }

    private class MetadataImpl(var value: Any? = null) : Metadata {

        override fun asMessageLog() = value as? String

        override fun asMessage() = value as? Message

        override fun asIdleHandler() = value as? IdleHandler

        override fun asMotionEvent() = value as? MotionEvent

        override fun toString(): String {
            asMessageLog()?.let { return it }
            asMessage()?.let { return it.toString() }
            asIdleHandler()?.let { return "IdleHandler { name=${it.javaClass.name ?: ""} }" }
            asMotionEvent()?.let { return it.toString() }
            return "Metadata { value=null }"
        }
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
