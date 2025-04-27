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

import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting

/**
 * 主线程[Looper]观察者
 *
 * @author xcc
 * @date 2025/3/27
 */
internal sealed class LooperWatcher {

    /**
     * 观察者被GC后，调用[thunk]
     */
    @MainThread
    abstract fun trackGC(thunk: Runnable)

    @MainThread
    @VisibleForTesting
    abstract fun remove()
}