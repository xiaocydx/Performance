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

package com.xiaocydx.performance.plugin.generate

import com.xiaocydx.performance.plugin.generate.Record.Companion.ID_SLICE
import java.io.File

/**
 * @author xcc
 * @date 2025/4/24
 */
internal interface MetricsParser<T : Any> {

    fun match(tag: String): Class<T>?

    fun json(file: File, metrics: T, context: GenerateContext): String?

    fun filter(snapshot: List<Long>): List<Long> {
        if (snapshot.isEmpty()) return snapshot
        val outcome = snapshot.toMutableList()
        val first = Record(snapshot.first())
        val last = Record(snapshot.last())
        if (first.id == ID_SLICE) {
            outcome.removeFirst()
        }
        if (outcome.isNotEmpty() && last.id == ID_SLICE) {
            outcome.removeLast()
        }
        return outcome
    }
}