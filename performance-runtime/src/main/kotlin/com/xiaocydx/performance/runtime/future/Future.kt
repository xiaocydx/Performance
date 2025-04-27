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

package com.xiaocydx.performance.runtime.future

import android.os.Message
import android.os.MessageQueue
import com.xiaocydx.performance.runtime.Reflection

/**
 * @author xcc
 * @date 2025/4/25
 */
internal object Future : Reflection {
    private val mMessagesField = runCatching {
        MessageQueue::class.java.toSafe().declaredInstanceFields.find("mMessages")
    }.getOrNull()?.apply { isAccessible = true }

    private val nextField = runCatching {
        Message::class.java.toSafe().declaredInstanceFields.find("next")
    }.getOrNull()?.apply { isAccessible = true }

    fun getPendingList(queue: MessageQueue, uptimeMillis: Long): List<Pending> {
        if (mMessagesField == null || nextField == null) return emptyList()
        val outcome = mutableListOf<Pending>()
        synchronized(queue) {
            var message = mMessagesField.get(queue) as? Message
            while (message != null) {
                // TODO: 补充wait时长
                outcome.add(Pending(
                    `when` = message.`when`,
                    what = message.what,
                    targetName = message.target?.javaClass?.name ?: "",
                    callbackName = message.callback?.javaClass?.name ?: "",
                    arg1 = message.arg1,
                    arg2 = message.arg2
                ))
                message = nextField.get(message) as? Message
            }
        }
        return outcome
    }
}