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

package com.xiaocydx.performance.looper

import android.os.Looper
import android.os.MessageQueue
import android.os.MessageQueue.IdleHandler
import androidx.annotation.MainThread
import com.xiaocydx.performance.Reflection
import com.xiaocydx.performance.log
import com.xiaocydx.performance.reference.Cleaner

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class MainLooperIdleAnalyzer private constructor() {
    private val list = IdleHandlerList()
    private var canTrackGC = false

    @MainThread
    fun trackGC(thunk: Runnable) {
        if (canTrackGC) Cleaner.add(list, thunk)
    }

    @MainThread
    private fun set(original: List<IdleHandler>?) {
        canTrackGC = original != null
        original?.forEach { list.add(it) }
    }

    @MainThread
    private fun get(): List<IdleHandler> = list

    private class IdleHandlerList : ArrayList<IdleHandler>() {
        private val map = mutableMapOf<IdleHandler, IdleHandlerWrapper>()

        override fun add(element: IdleHandler): Boolean {
            val wrapper = IdleHandlerWrapper(element)
            map[element] = wrapper
            return super.add(wrapper)
        }

        override fun remove(element: IdleHandler): Boolean {
            val wrapper = when (element) {
                is IdleHandlerWrapper -> map.remove(element.delegate)
                else -> map.remove(element)
            }
            return super.remove(wrapper ?: element)
        }
    }

    private class IdleHandlerWrapper(val delegate: IdleHandler) : IdleHandler {

        override fun queueIdle(): Boolean {
            val startTime = System.currentTimeMillis()
            log { "MainLooperIdleAnalyzer start" }
            val keep = delegate.queueIdle()
            log { "MainLooperIdleAnalyzer end, ${System.currentTimeMillis() - startTime}ms" }
            return keep
        }
    }

    companion object : Reflection {

        @MainThread
        @Suppress("UNCHECKED_CAST")
        fun setup(): MainLooperIdleAnalyzer {
            val analyzer = MainLooperIdleAnalyzer()
            runCatching {
                val fields = MessageQueue::class.java.toSafe().declaredInstanceFields
                val mIdleHandlers = fields.find("mIdleHandlers").apply { isAccessible = true }
                val original = mIdleHandlers.get(Looper.getMainLooper().queue) as? List<IdleHandler>
                analyzer.set(original)
                mIdleHandlers.set(Looper.getMainLooper().queue, analyzer.get())
            }
            return analyzer
        }
    }
}