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

package com.xiaocydx.performance.analyzer

import android.os.Process
import com.xiaocydx.performance.runtime.history.sample.ThreadStat

/**
 * @author xcc
 * @date 2025/4/27
 */
interface Metrics {

    /**
     * 可用于区分解析逻辑
     */
    val tag: String

    /**
     * [Process.myPid]
     */
    val pid: Int

    /**
     * [Process.myTid]
     */
    val tid: Int

    /**
     * 可用于定义文件名
     */
    val createTimeMillis: Long
}

internal fun ThreadStat.toCause(): RuntimeException {
    val cause = RuntimeException()
    cause.stackTrace = stack.toTypedArray()
    return cause
}