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

package com.xiaocydx.performance.fake

import android.os.Looper
import android.os.Message

/**
 * @author xcc
 * @date 2025/3/19
 */
internal interface FakeLooperObserver {
    fun messageDispatchStarting()

    fun messageDispatched(msg: Message)

    fun dispatchingThrewException(msg: Message, exception: Exception)
}

internal fun FakeLooperObserver.toReal(original: Any?): Any {
    val delegate = original as? Looper.Observer
    return object : Looper.Observer {
        override fun messageDispatchStarting(): Any? {
            val token = delegate?.messageDispatchStarting()
            this@toReal.messageDispatchStarting()
            return token
        }

        override fun messageDispatched(token: Any?, msg: Message) {
            delegate?.messageDispatched(token, msg)
            this@toReal.messageDispatched(msg)
        }

        override fun dispatchingThrewException(token: Any?, msg: Message, exception: Exception) {
            delegate?.dispatchingThrewException(token, msg, exception)
            this@toReal.dispatchingThrewException(msg, exception)
        }
    }
}