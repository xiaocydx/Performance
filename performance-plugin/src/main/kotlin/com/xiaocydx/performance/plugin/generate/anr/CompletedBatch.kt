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

import com.xiaocydx.performance.plugin.generate.Sample

/**
 * @author xcc
 * @date 2025/4/30
 */
internal data class CompletedBatch(
    val count: Int,
    val scene: String,
    val lastMetadata: String,
    val startUptimeMillis: Long,
    val startThreadTimeMillis: Long,
    val endUptimeMillis: Long,
    val endThreadTimeMillis: Long,
    val idleDurationMillis: Long,
    val snapshot: List<Long>,
    val sampleList: List<Sample>
) {
    val wallDurationMillis = endUptimeMillis - startUptimeMillis
    val cpuDurationMillis = if (endThreadTimeMillis == 0L) -1 else endThreadTimeMillis - startThreadTimeMillis
}