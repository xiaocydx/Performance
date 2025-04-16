package com.xiaocydx.performance.analyzer.block

import com.xiaocydx.performance.runtime.history.Snapshot

/**
 * @author xcc
 * @date 2025/4/16
 */
class BlockReport(
    val scene: String,
    val snapshot: Snapshot,
    val durationMillis: Long,
    val thresholdMillis: Long,
    val isRecordEnabled: Boolean,
)