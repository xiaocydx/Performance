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

package com.xiaocydx.performance.reference

import androidx.annotation.AnyThread
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xcc
 * @date 2025/3/19
 */
internal object PerformanceDaemons {
    private val isStarted = AtomicBoolean(false)
    private val referenceQueueDaemon = ReferenceQueueDaemon()
    private val referenceWatchdogDaemon = ReferenceWatchdogDaemon()

    @AnyThread
    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        arrayOf(referenceQueueDaemon, referenceWatchdogDaemon).forEach { it.start() }
    }

    private abstract class Daemon(val name: String) : Runnable {

        fun start() {
            val thread = Thread(this, name)
            thread.isDaemon = true
            thread.start()
        }
    }

    private class ReferenceQueueDaemon : Daemon("PerformanceReferenceQueueDaemon") {
        override fun run() {
            while (true) {
                val reference = Cleaner.queue.remove() as Cleaner
                reference.clean()
            }
        }
    }

    private class ReferenceWatchdogDaemon : Daemon("PerformanceReferenceWatchdogDaemon") {
        private val lock = this as Object

        override fun run() {
            synchronized(lock) {
                lock.wait()
            }
        }
    }
}

