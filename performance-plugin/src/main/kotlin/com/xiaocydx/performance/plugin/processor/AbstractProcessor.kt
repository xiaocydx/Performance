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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.xiaocydx.performance.plugin.processor

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author xcc
 * @date 2025/4/13
 */
internal abstract class AbstractProcessor {

    protected inline fun Dispatcher.execute(
        tasks: TaskCountDownLatch,
        crossinline task: () -> Unit,
    ) {
        tasks.increment()
        execute {
            task()
            tasks.decrement()
        }
    }

    protected class TaskCountDownLatch {
        private val count = AtomicInteger()
        private val lock = this as Object

        fun increment() {
            count.incrementAndGet()
        }

        fun decrement() {
            val count = count.decrementAndGet()
            require(count >= 0) { "计数异常" }
            if (count == 0) {
                synchronized(lock) {
                    lock.notifyAll()
                }
            }
        }

        fun await() {
            synchronized(lock) {
                while (count.get() > 0) {
                    lock.wait()
                }
            }
        }
    }
}

internal fun <R> Future<R>.await() = get()