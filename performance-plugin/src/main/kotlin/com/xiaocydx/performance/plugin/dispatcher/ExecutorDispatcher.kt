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

package com.xiaocydx.performance.plugin.dispatcher

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class ExecutorDispatcher(threads: Int) : Dispatcher {
    private val executor = Executors.newFixedThreadPool(threads.coerceAtLeast(1))

    override fun <R> submit(task: Callable<R>): Future<R> {
        return executor.submit(task)
    }

    override fun shutdownNow() {
        executor.shutdownNow()
    }
}