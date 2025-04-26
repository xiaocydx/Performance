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

import android.os.Build
import java.io.File

/**
 * [/proc/stat解析](https://gityuan.com/2017/08/12/proc_stat/)
 */
internal class ProcSysStat private constructor(
    val isAvailable: Boolean = false,
    val user: Long = 0L,
    val nice: Long = 0L,
    val system: Long = 0L,
    val idle: Long = 0L,
    val iowait: Long = 0L,
    val irq: Long = 0L,
    val softirq: Long = 0L
) {
    val total = user + nice + system + idle + iowait + irq + softirq

    companion object {

        fun read(): ProcSysStat {
            if (Build.VERSION.SDK_INT < 26) {
                runCatching {
                    val result: List<String>
                    val file = File("/proc/stat")
                    file.bufferedReader().use {
                        result = it.readLine().split(" ")
                        return@use
                    }
                    return ProcSysStat(
                        isAvailable = true,
                        user = result[2].toLong(),
                        nice = result[3].toLong(),
                        system = result[4].toLong(),
                        idle = result[5].toLong(),
                        iowait = result[6].toLong(),
                        irq = result[7].toLong(),
                        softirq = result[8].toLong(),
                    )
                }
            }
            return ProcSysStat()
        }
    }
}

/**
 * [/proc/stat解析](https://gityuan.com/2017/08/12/proc_stat/)
 */
internal class ProcPidStat private constructor(
    val isAvailable: Boolean = false,
    val pid: Int = 0,
    val utime: Long = 0L,
    val stime: Long = 0L,
    val cutime: Long = 0L,
    val cstime: Long = 0L,
    val priority: Int = 0,
    val nice: Int = 0
) {
    val total = utime + stime + cutime + cstime

    companion object {

        fun read(pid: Int): ProcPidStat {
            val file = File("/proc/${pid}/stat")
            runCatching {
                val result: List<String>
                file.bufferedReader().use {
                    result = it.readLine().split(" ")
                    return@use
                }
                require(pid == result[0].toInt())
                return ProcPidStat(
                    isAvailable = true,
                    pid = result[0].toInt(),
                    utime = result[13].toLong(),
                    stime = result[14].toLong(),
                    cutime = result[15].toLong(),
                    cstime = result[16].toLong(),
                    priority = result[17].toInt(),
                    nice = result[18].toInt()
                )
            }
            return ProcPidStat()
        }
    }
}