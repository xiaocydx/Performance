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

package com.xiaocydx.performance.gc

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class Cleaner private constructor(
    referent: Any,
    private val thunk: Runnable
) : PhantomReference<Any>(referent, queue) {

    fun clean() {
        if (!remove(this)) return
        thunk.run()
    }

    companion object {
        // TODO: 改为双向链表
        private val list = mutableListOf<Cleaner>()
        val queue = ReferenceQueue<Any>()

        @Synchronized
        fun add(referent: Any, thunk: Runnable) {
            list.add(Cleaner(referent, thunk))
        }

        @Synchronized
        private fun remove(cleaner: Cleaner): Boolean {
            return list.remove(cleaner)
        }
    }
}