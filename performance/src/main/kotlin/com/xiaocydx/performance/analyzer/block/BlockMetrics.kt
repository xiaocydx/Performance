package com.xiaocydx.performance.analyzer.block

import android.os.Process
import com.xiaocydx.performance.runtime.history.History
import com.xiaocydx.performance.runtime.history.Snapshot

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
    val lastActivity: String,
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
     * 线程采样状态
     */
    val sampleState: String?,
    /**
     * 线程采样堆栈
     */
    val sampleStack: Array<StackTraceElement>?
)