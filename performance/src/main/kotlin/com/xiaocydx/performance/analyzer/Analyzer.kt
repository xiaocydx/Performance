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

package com.xiaocydx.performance.analyzer

import com.xiaocydx.performance.Host
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xcc
 * @date 2025/4/4
 */
internal abstract class Analyzer(protected val host: Host) {
    private val isStarted = AtomicBoolean(false)
    protected val coroutineScope = host.createMainScope()

    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        init()
    }

    fun stop() {
        if (!isStarted.compareAndSet(true, false)) return
        coroutineScope.coroutineContext.cancelChildren()
    }

    protected abstract fun init()
}