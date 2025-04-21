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

import android.os.SystemClock

/**
 * @author xcc
 * @date 2025/4/21
 */
internal class LooperDispatcher(private val callback: LooperCallback) {
    private var dispatchingScene: Scene? = null
    private val current = DispatchContextImpl()

    fun start(scene: Scene, value: Any?) {
        if (dispatchingScene != null) return
        dispatchingScene = scene
        makeCurrent {
            isStart = true
            this.scene = scene
            this.value = value
        }
        callback.dispatch(current)
    }

    fun end(scene: Scene, value: Any?) {
        if (dispatchingScene != scene) return
        dispatchingScene = null
        makeCurrent {
            isStart = false
            this.scene = scene
            this.value = value
        }
        callback.dispatch(current)
    }

    private inline fun makeCurrent(block: DispatchContextImpl.() -> Unit) {
        current.apply(block)
        current.uptimeMillis = SystemClock.uptimeMillis()
        current.threadTimeMillis = SystemClock.currentThreadTimeMillis()
    }

    private class DispatchContextImpl : DispatchContext {
        override var isStart = false
        override var scene = Scene.Message
        override var value: Any? = null
        override var uptimeMillis = 0L
        override var threadTimeMillis = 0L
    }
}
