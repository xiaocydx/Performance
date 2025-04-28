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

package com.xiaocydx.performance.runtime.signal

import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread

/**
 * @author xcc
 * @date 2025/4/27
 */
internal object Signal {
    @Volatile private var anrCallback: ANRCallback? = null

    init {
        System.loadLibrary("performance_runtime")
    }

    @MainThread
    fun setANRCallback(current: ANRCallback?) {
        assertMainThread()
        val previous = anrCallback
        if (previous == null && current != null) {
            nativeRegister()
        } else if (previous != null && current == null) {
            nativeUnregister()
        }
        anrCallback = current
    }

    @JvmStatic
    private external fun nativeRegister()

    @JvmStatic
    private external fun nativeUnregister()

    // Called from native code
    @JvmStatic
    private fun anr() {
        val callback = anrCallback
        callback?.maybeANR()
    }
}