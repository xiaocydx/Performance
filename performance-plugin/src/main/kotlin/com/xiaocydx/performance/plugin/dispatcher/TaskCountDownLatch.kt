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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * @author xcc
 * @date 2025/4/18
 */
internal class TaskCountDownLatch {
    private val count = AtomicInteger()
    private var error = AtomicReference<Throwable>(null)

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = this as Object

    fun increment() {
        count.incrementAndGet()
    }

    fun decrement() {
        val count = count.decrementAndGet()
        if (count <= 0) synchronized(lock) { lock.notifyAll() }
    }

    fun await() {
        throwError()
        synchronized(lock) { while (count.get() > 0) lock.wait() }
        throwError()
    }

    fun setError(cause: Throwable) {
        if (!error.compareAndSet(null, cause)) return
        do {
            val count = count.get()
        } while (count > 0 && !this.count.compareAndSet(count, 0))
        synchronized(lock) { lock.notifyAll() }
    }

    fun hasError(): Boolean {
        return error.get() != null
    }

    private fun throwError() {
        error.get()?.let { throw it }
    }
}

internal inline fun Dispatcher.execute(
    tasks: TaskCountDownLatch,
    crossinline task: () -> Unit,
) {
    if (tasks.hasError()) return
    tasks.increment()
    execute {
        try {
            if (!tasks.hasError()) {
                task()
                tasks.decrement()
            }
        } catch (e: Throwable) {
            tasks.setError(e)
        }
    }
}