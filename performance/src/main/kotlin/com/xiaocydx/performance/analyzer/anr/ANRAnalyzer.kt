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

package com.xiaocydx.performance.analyzer.anr

import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.runtime.looper.MainLooperCallback
import com.xiaocydx.performance.runtime.looper.MainLooperCallback.Type

/**
 * @author xcc
 * @date 2025/3/27
 */
internal class ANRAnalyzer(private val host: Performance.Host) : MainLooperCallback {

    fun init() {
    }

    override fun start(type: Type, data: Any?) {

    }

    override fun end(type: Type, data: Any?) {
        // TODO: 收集data的信息进队列
    }
}