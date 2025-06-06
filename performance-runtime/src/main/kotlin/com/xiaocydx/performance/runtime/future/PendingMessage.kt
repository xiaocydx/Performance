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

import com.xiaocydx.performance.runtime.looper.Metadata

/**
 * @author xcc
 * @date 2025/4/25
 */
data class PendingMessage(
    val `when`: Long,
    val what: Int,
    val targetName: String?,
    val callbackName: String?,
    val arg1: Int,
    val arg2: Int,
    val uptimeMillis: Long
) {

    override fun toString(): String {
        return Metadata.messageToString(
            `when` = `when`,
            what = what,
            targetName = targetName,
            callbackName = callbackName,
            arg1 = arg1,
            arg2 = arg2,
            uptimeMillis = uptimeMillis
        )
    }
}