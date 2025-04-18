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
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/**
 * @author xcc
 * @date 2025/4/13
 */
internal interface SerialDispatcher : Dispatcher {

    companion object {
        fun nop(): SerialDispatcher = NopDispatcher()
        fun single(): SerialDispatcher = SingleDispatcher()
        fun sync(): SerialDispatcher = SyncDispatcher()
    }
}

private class NopDispatcher : SerialDispatcher {

    override fun execute(task: Runnable) {
        task.run()
    }

    override fun <R> submit(task: Callable<R>): Future<R> {
        val future = FutureTask(task)
        future.run()
        return future
    }

    override fun shutdownNow() = Unit
}

private class SingleDispatcher : SerialDispatcher {
    private val delegate = ExecutorDispatcher(threads = 1)

    override fun execute(task: Runnable) {
        delegate.execute(task)
    }

    override fun <R> submit(task: Callable<R>): Future<R> {
        return delegate.submit(task)
    }

    override fun shutdownNow() {
        delegate.shutdownNow()
    }
}

private class SyncDispatcher : SerialDispatcher {

    override fun execute(task: Runnable) {
        synchronized(this) { task.run() }
    }

    override fun <R> submit(task: Callable<R>): Future<R> {
        val future = FutureTask(task)
        synchronized(this) { future.run() }
        return future
    }

    override fun shutdownNow() = Unit
}