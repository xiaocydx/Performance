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

package com.xiaocydx.performance.plugin.metadata

import com.xiaocydx.performance.plugin.metadata.Metadata.Companion.INITIAL_ID
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author xcc
 * @date 2025/4/15
 */
internal interface Metadata {

    fun toKey(): String

    fun toOutput(): String

    companion object {
        const val SEPARATOR = ','
        const val INITIAL_ID = 0
        val charset = Charsets.UTF_8
    }
}

internal class IdGenerator(initial: Int = INITIAL_ID) {
    private val id = AtomicInteger(initial)

    fun generate() = id.incrementAndGet()
}