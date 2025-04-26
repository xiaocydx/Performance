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

package com.xiaocydx.performance.runtime.history.sample

import android.os.Process
import java.io.File

/**
 * [/proc/stat解析](https://gityuan.com/2017/08/12/proc_stat/)
 *
 * @author xcc
 * @date 2025/4/21
 */
internal class ProcStat private constructor(
    val pid: Int,
    val priority: Int,
    val nice: Int
) {
    companion object {

        fun get(pid: Int): ProcStat {
            val file = File("/proc/${Process.myPid()}/stat")
            var priority = Int.MIN_VALUE
            var nice = Int.MAX_VALUE
            runCatching {
                val result: List<String>
                file.bufferedReader().use {
                    result = it.readLine().split(" ")
                }
                priority = result[17].toInt()
                nice = result[18].toInt()
            }
            return ProcStat(pid, priority, nice)
        }
    }
}