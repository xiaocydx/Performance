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

package com.xiaocydx.performance.analyzer.anr

import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample

/**
 * @author xcc
 * @date 2025/4/29
 */
data class CompletedBatch(
    /**
     * 已完成的数量
     */
    val count: Int,

    /**
     * 已完成的场景
     */
    val scene: String,

    /**
     * [scene]最后一次调度的元数据
     */
    val lastMetadata: String,

    /**
     * 开始时间，可用于计算[wallDurationMillis]
     */
    val startUptimeMillis: Long,

    /**
     * 开始时间，可用于计算[cpuDurationMillis]
     */
    val startThreadTimeMillis: Long,

    /**
     * 结束时间，可用于计算[wallDurationMillis]
     */
    val endUptimeMillis: Long,

    /**
     * 结束时间，可用于计算[cpuDurationMillis]
     */
    val endThreadTimeMillis: Long,

    /**
     * [wallDurationMillis]包含的空闲时长
     */
    val idleDurationMillis: Long,

    /**
     * 调用栈快照
     */
    val snapshot: Snapshot,

    /**
     * 采样数据集合
     */
    val sampleList: List<Sample>
) {
    val wallDurationMillis: Long
        get() = endUptimeMillis - startUptimeMillis

    val cpuDurationMillis: Long
        get() = if (endThreadTimeMillis == 0L) -1 else endThreadTimeMillis - startThreadTimeMillis
}