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

import android.os.Process
import com.xiaocydx.performance.analyzer.Metrics
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
    override val createTimeMillis: Long
) : Metrics {
    override val tag = "ANRMetrics"
}

data class Group(
    val count: Int,
    val scene: String,
    val startUptimeMillis: Long,
    val startThreadTimeMillis: Long,
    val endUptimeMillis: Long,
    val endThreadTimeMillis: Long,
    val metadata: String,
    val snapshot: Snapshot,
    val sampleList: List<Sample>
)