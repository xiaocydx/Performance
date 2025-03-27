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

package com.xiaocydx.performance.watcher.looper

import android.os.Looper
import android.os.MessageQueue
import androidx.annotation.MainThread
import com.xiaocydx.performance.Reflection
import com.xiaocydx.performance.reference.Cleaner
import com.xiaocydx.performance.watcher.looper.MainLooperCallback.Type

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class MainLooperIdleHandlerWatcher private constructor(
    private val callback: MainLooperCallback
) : MainLooperWatcher() {
    private val list = IdleHandlerList()
    private var canTrackGC = false

    override fun trackGC(thunk: Runnable) {
        if (canTrackGC) Cleaner.add(list, thunk)
    }

    override fun remove() = Unit

    @MainThread
    private fun set(original: List<MessageQueue.IdleHandler>?) {
        canTrackGC = original != null
        original?.forEach { list.add(it) }
    }

    @MainThread
    private fun get(): List<MessageQueue.IdleHandler> = list

    private inner class IdleHandlerList : ArrayList<MessageQueue.IdleHandler>() {
        private val map = mutableMapOf<MessageQueue.IdleHandler, IdleHandlerWrapper>()

        override fun add(element: MessageQueue.IdleHandler): Boolean {
            val wrapper = IdleHandlerWrapper(element)
            map[element] = wrapper
            return super.add(wrapper)
        }

        override fun remove(element: MessageQueue.IdleHandler): Boolean {
            val wrapper = when (element) {
                is IdleHandlerWrapper -> map.remove(element.delegate)
                else -> map.remove(element)
            }
            return super.remove(wrapper ?: element)
        }
    }

    private inner class IdleHandlerWrapper(
        val delegate: MessageQueue.IdleHandler
    ) : MessageQueue.IdleHandler {

        override fun queueIdle(): Boolean {
            callback.start(msg = null, type = Type.IdleHandler)
            val keep = delegate.queueIdle()
            callback.end(msg = null, type = Type.IdleHandler)
            return keep
        }
    }

    companion object : Reflection {

        @MainThread
        @Suppress("UNCHECKED_CAST")
        fun setup(
            mainLooper: Looper,
            callback: MainLooperCallback
        ): MainLooperIdleHandlerWatcher {
            val watcher = MainLooperIdleHandlerWatcher(callback)
            runCatching {
                val fields = MessageQueue::class.java.toSafe().declaredInstanceFields
                val mIdleHandlers = fields.find("mIdleHandlers").apply { isAccessible = true }
                val original = mIdleHandlers.get(mainLooper.queue) as? List<MessageQueue.IdleHandler>
                watcher.set(original)
                mIdleHandlers.set(mainLooper.queue, watcher.get())
            }
            return watcher
        }
    }
}