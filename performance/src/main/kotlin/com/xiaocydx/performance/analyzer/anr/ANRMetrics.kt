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

@file:Suppress("KDocUnresolvedReference")

package com.xiaocydx.performance.analyzer.anr

import android.os.Process
import com.xiaocydx.performance.analyzer.Metrics
import com.xiaocydx.performance.analyzer.block.BlockMetricsConfig
import com.xiaocydx.performance.runtime.future.Pending
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample

/**
 * @author xcc
 * @date 2025/4/27
 */
data class ANRMetrics(
    /**
     * [Process.myPid]
     */
    override val pid: Int,

    /**
     * [Process.myTid]
     */
    override val tid: Int,

    /**
     * 可用于定义文件名
     */
    override val createTimeMillis: Long,

    /**
     * 最后启动的Activity
     */
    val latestActivity: String,

    /**
     * 卡顿阈值，来自[BlockMetricsConfig]
     */
    val thresholdMillis: Long,

    /**
     * 是否已启用[History]的Record。若未启用，则[snapshot]为空。
     */
    val isRecordEnabled: Boolean,

    val anrSample: Sample,

    val history: List<Group>,

    val future: List<Pending>
) : Metrics {

    /**
     * 可用于区分解析逻辑
     */
    override val tag = "ANRMetrics"
}

data class Group(
    val count: Int,

    val scene: String,

    /**
     * [scene]的元数据
     */
    val metadata: String,

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
     * 包含的空闲时长
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