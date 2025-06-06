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

package com.xiaocydx.performance.runtime.gc

/**
 * @author xcc
 * @date 2025/4/1
 */
internal class ReferenceQueueDaemon : Runnable {

    override fun run() {
        while (true) {
            val reference = Cleaner.queue.remove() as Cleaner
            reference.clean()
        }
    }

    fun start() {
        val thread = Thread(this, "PerformanceReferenceQueueDaemon")
        thread.isDaemon = true
        thread.start()
    }
}