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

package com.xiaocydx.performance.plugin.generate.anr

/**
 * @author xcc
 * @date 2025/4/30
 */
internal data class PendingMessage(
    val uptimeMillis: Long,
    val `when`: Long,
    val what: Int,
    val targetName: String?,
    val callbackName: String?,
    val arg1: Int,
    val arg2: Int
) {

    override fun toString(): String {
        val b = StringBuilder()
        b.append("Message { when=").append(`when` - uptimeMillis).append("ms")
        if (targetName != null) {
            if (callbackName != null) {
                b.append(", callback=").append(callbackName)
            } else {
                b.append(", what=").append(what)
            }
            if (arg1 != 0) b.append(", arg1=").append(arg1)
            if (arg2 != 0) b.append(", arg1=").append(arg1)
            b.append(", target=").append(targetName)
        } else {
            b.append(", barrier=").append(arg1)
        }
        b.append(" }")
        return b.toString()
    }
}