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

/**
 * @author xcc
 * @date 2025/4/24
 */
internal data class Sample(
    val uptimeMillis: Long = 0L,
    val cpuStat: CPUStat? = null,
    val threadStat: ThreadStat = ThreadStat()
)

internal data class CPUStat(
    val cpu: String = "",
    val user: String = "",
    val system: String = "",
    val idle: String = "",
    val iowait: String = "",
    val app: String = ""
)

internal data class ThreadStat(
    val priority: String = "",
    val nice: String = "",
    val state: String = "",
    val stack: List<String> = emptyList()
)