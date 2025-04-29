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
import com.xiaocydx.performance.runtime.future.PendingMessage
import com.xiaocydx.performance.runtime.history.sample.Sample

/**
 * ANR指标数据
 *
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
     * 是否已启用[History]的Record。若未启用，则[CompletedBatch.snapshot]为空。
     */
    val isRecordEnabled: Boolean,

    /**
     * 发生ANR时的采样数据
     */
    val anrSample: Sample,

    /**
     * 已完成的调度
     */
    val history: List<CompletedBatch>,

    /**
     * 待调度的消息
     */
    val future: List<PendingMessage>
) : Metrics {

    /**
     * 可用于区分解析逻辑
     */
    override val tag = "ANRMetrics"
}