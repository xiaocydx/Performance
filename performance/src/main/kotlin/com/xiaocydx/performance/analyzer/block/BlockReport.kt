package com.xiaocydx.performance.analyzer.block

import com.xiaocydx.performance.runtime.history.Snapshot

/**
 * @author xcc
 * @date 2025/4/16
 */
class BlockReport(
    val scene: String,
    val value: String,
    val lastActivity: String,
    val priority: Int,
    val nice: Int,
    val snapshot: Snapshot,
    val thresholdMillis: Long,
    val wallDurationMillis: Long,
    val cpuDurationMillis: Long,
    val isRecordEnabled: Boolean
)