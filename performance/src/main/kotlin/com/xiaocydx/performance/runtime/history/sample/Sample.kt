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

package com.xiaocydx.performance.runtime.history.sample

import android.os.SystemClock

/**
 * @author xcc
 * @date 2025/4/24
 */
class Sample private constructor(
    val uptimeMillis: Long,
    val threadState: Thread.State,
    val threadStack: List<StackTraceElement>
) {

    companion object {
        fun current(thread: Thread) = Sample(
            uptimeMillis = SystemClock.uptimeMillis(),
            threadState = thread.state,
            threadStack = thread.stackTrace.toList()
        )
    }
}