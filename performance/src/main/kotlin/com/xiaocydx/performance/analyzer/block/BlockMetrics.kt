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

package com.xiaocydx.performance.analyzer.block

import android.os.Process
import com.xiaocydx.performance.runtime.history.sample.SampleData
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.record.Snapshot

/**
 * 卡顿指标
 *
 * @author xcc
 * @date 2025/4/16
 */
class BlockMetrics(
    /**
     * [Process.myTid]
     */
    val pid: Int,
    /**
     * [Process.myTid]
     */
    val tid: Int,
    /**
     * 卡顿场景
     */
    val scene: String,
    /**
     * 最后启动的Activity
     */
    val latestActivity: String,
    /**
     * 主线程priority值
     */
    val priority: Int,
    /**
     * 主线程nice值
     */
    val nice: Int,
    /**
     * 创建时间
     */
    val createTimeMillis: Long,
    /**
     * 卡顿阈值
     */
    val thresholdMillis: Long,
    /**
     * 执行时间总和
     */
    val wallDurationMillis: Long,
    /**
     * CPU时间总和
     */
    val cpuDurationMillis: Long,
    /**
     * 是否已启用[History]的Record。若未启用，则[snapshot]为空。
     */
    val isRecordEnabled: Boolean,
    /**
     * [scene]的元数据
     */
    val metadata: String,
    /**
     * 线程调用栈快照
     */
    val snapshot: Snapshot,
    /**
     * 线程采样数据
     */
    val sampleData: SampleData?
)