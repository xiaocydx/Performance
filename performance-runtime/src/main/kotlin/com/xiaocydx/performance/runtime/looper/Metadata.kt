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

import android.os.Message
import android.os.MessageQueue.IdleHandler
import android.view.KeyEvent
import android.view.KeyEvent.keyCodeToString
import android.view.MotionEvent
import android.view.MotionEvent.actionToString

/**
 * @author xcc
 * @date 2025/5/1
 */
internal interface Metadata {

    fun asMessageLog(): String?

    fun asMessage(): Message?

    fun asIdleHandler(): IdleHandler?

    fun asMotionEvent(): MotionEvent?

    fun asKeyEvent(): KeyEvent?

    override fun toString(): String

    fun toString(uptimeMillis: Long): String {
        asMessageLog()?.let { return it }
        asMessage()?.let { return messageToString(it, uptimeMillis) }
        asIdleHandler()?.let { return idleHandlerToString(it) }
        asMotionEvent()?.let { return motionEventToString(it) }
        asKeyEvent()?.let { return keyEventToString(it) }
        return ""
    }

    companion object {

        fun messageToString(message: Message, uptimeMillis: Long): String {
            return messageToString(
                `when` = message.`when`,
                what = message.what,
                targetName = message.target?.javaClass?.name,
                callbackName = message.callback?.javaClass?.name,
                arg1 = message.arg1,
                arg2 = message.arg2,
                uptimeMillis = uptimeMillis
            )
        }

        fun messageToString(
            `when`: Long,
            what: Int,
            targetName: String?,
            callbackName: String?,
            arg1: Int,
            arg2: Int,
            uptimeMillis: Long
        ): String {
            val b = StringBuilder()
            b.append("{ when=").append(`when` - uptimeMillis).append("ms")
            if (targetName != null) {
                if (callbackName != null) {
                    b.append(" callback=").append(callbackName)
                } else {
                    b.append(" what=").append(what)
                }
                if (arg1 != 0) b.append(" arg1=").append(arg1)
                if (arg2 != 0) b.append(" arg1=").append(arg1)
                b.append(" target=").append(targetName)
            } else {
                b.append(" barrier=").append(arg1)
            }
            b.append(" }")
            return b.toString()
        }

        fun idleHandlerToString(idleHandler: IdleHandler): String {
            return idleHandlerToString(idleHandler.javaClass.name ?: "")
        }

        fun idleHandlerToString(idleHandlerName: String): String {
            return "IdleHandler { name=${idleHandlerName} }"
        }

        fun motionEventToString(event: MotionEvent): String {
            return motionEventToString(event.action, event.x, event.y)
        }

        fun motionEventToString(action: Int, x: Float, y: Float): String {
            return "MotionEvent { action=${actionToString(action)}, x=$x, y=$y }"
        }

        fun keyEventToString(event: KeyEvent): String {
            return keyEventToString(event.action, event.keyCode)
        }

        fun keyEventToString(action: Int, keyCode: Int): String {
            @Suppress("DEPRECATION")
            val actionToString = when (action) {
                KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
                KeyEvent.ACTION_UP -> "ACTION_UP"
                KeyEvent.ACTION_MULTIPLE -> "ACTION_MULTIPLE"
                else -> action.toString()
            }
            return "KeyEvent { action=${actionToString}, keyCode=${keyCodeToString(keyCode)} }"
        }
    }
}