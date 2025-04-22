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

package com.xiaocydx.performance.runtime.looper

import android.os.MessageQueue
import android.os.MessageQueue.IdleHandler
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.Reflection
import com.xiaocydx.performance.runtime.gc.Cleaner

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class LooperIdleHandlerWatcher private constructor(
    private val dispatcher: LooperDispatcher
) : LooperWatcher() {
    private val list = IdleHandlerList()
    private var canTrackGC = false

    override fun trackGC(thunk: Runnable) {
        if (canTrackGC) Cleaner.add(list, thunk)
    }

    override fun remove() = Unit

    @MainThread
    private fun set(original: List<IdleHandler>?) {
        canTrackGC = original != null
        original?.forEach { list.add(it) }
    }

    @MainThread
    private fun get(): List<IdleHandler> = list

    private inner class IdleHandlerList : ArrayList<IdleHandler>() {
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

    private inner class IdleHandlerWrapper(val delegate: IdleHandler) : IdleHandler {

        override fun queueIdle(): Boolean {
            dispatcher.start(scene = Scene.IdleHandler, metadata = delegate)
            val keep = delegate.queueIdle()
            dispatcher.end(scene = Scene.IdleHandler, metadata = delegate)
            return keep
        }
    }

    companion object : Reflection {

        @MainThread
        @Suppress("UNCHECKED_CAST")
        fun setup(
            mainQueue: MessageQueue,
            dispatcher: LooperDispatcher,
        ): LooperIdleHandlerWatcher {
            val watcher = LooperIdleHandlerWatcher(dispatcher)
            runCatching {
                val fields = MessageQueue::class.java.toSafe().declaredInstanceFields
                val mIdleHandlers = fields.find("mIdleHandlers").apply { isAccessible = true }
                val original = mIdleHandlers.get(mainQueue) as? List<IdleHandler>
                watcher.set(original)
                mIdleHandlers.set(mainQueue, watcher.get())
            }
            return watcher
        }
    }
}