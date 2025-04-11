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

import android.view.MotionEvent
import android.view.Window
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class MainLooperNativeTouchWatcher private constructor(
    private val window: Window,
    private val callback: MainLooperCallback
) : MainLooperWatcher() {
    private val windowCallback = WindowCallbackImpl(window.callback)

    override fun trackGC(thunk: Runnable) {
        // 业务场景不该出现windowCallback.delegate被移除的情况
    }

    override fun remove() {
        window.callback = windowCallback.delegate
    }

    private inner class WindowCallbackImpl(
        val delegate: Window.Callback
    ) : Window.Callback by delegate {

        @MainThread
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            callback.start(Type.NativeTouch, data = event)
            val consumed = delegate.dispatchTouchEvent(event)
            callback.end(Type.NativeTouch, data = event)
            return consumed
        }
    }

    companion object {

        @MainThread
        fun setup(
            window: Window,
            callback: MainLooperCallback
        ): MainLooperWatcher {
            val watcher = MainLooperNativeTouchWatcher(window, callback)
            window.callback = watcher.windowCallback
            return watcher
        }
    }
}