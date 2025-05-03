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
import android.os.SystemClock
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

    private val isAvailable: Boolean
        get() = mMessagesField != null || nextField != null

    fun getFirstPending(
        queue: MessageQueue,
        uptimeMillis: Long = SystemClock.uptimeMillis()
    ): PendingMessage? {
        if (!isAvailable) return null
        val message = synchronized(queue) { mMessagesField!!.get(queue) as? Message }
        return message?.toPending(uptimeMillis)
    }

    fun getPendingList(
        queue: MessageQueue,
        uptimeMillis: Long = SystemClock.uptimeMillis()
    ): List<PendingMessage> {
        if (!isAvailable) return emptyList()
        val outcome = mutableListOf<PendingMessage>()
        synchronized(queue) {
            var message = mMessagesField!!.get(queue) as? Message
            while (message != null) {
                outcome.add(message.toPending(uptimeMillis))
                message = nextField!!.get(message) as? Message
            }
        }
        return outcome
    }

    private fun Message.toPending(uptimeMillis: Long): PendingMessage {
        return PendingMessage(
            `when` = `when`,
            what = what,
            targetName = target?.javaClass?.name,
            callbackName = callback?.javaClass?.name,
            arg1 = arg1,
            arg2 = arg2,
            uptimeMillis = uptimeMillis,
        )
    }
}