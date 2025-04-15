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

package com.xiaocydx.performance.convert

import com.google.gson.Gson

/**
 * @author xcc
 * @date 2025/4/15
 */
internal object TracingJson {
    private val gson = Gson()
    private val max = MethodData(
        id = Record.ID_MAX, access = 0,
        className = "Record", methodName = "Max", desc = ""
    )
    private val slice = MethodData(
        id = Record.ID_SLICE, access = 0,
        className = "Record", methodName = "Slice", desc = ""
    )

    fun toJson(
        mapping: Map<Int, MethodData>,
        records: List<Record>,
    ): String {
        val fillMapping = mapping.toMutableMap()
        fillMapping[max.id] = max
        fillMapping[slice.id] = slice
        val events = records.map {
            val method = requireNotNull(fillMapping[it.id])
            TraceEvent(
                name = "${method.className}.${method.methodName}",
                ph = if (it.isEnter) TraceEvent.B else TraceEvent.E,
                ts = it.timeMs * 1000 // to microsecond
            )
        }
        return gson.toJson(events)
    }
}

/**
 * [Event Descriptions](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview?tab=t.0#heading=h.uxpopqvbjezh)
 */
internal data class TraceEvent(
    val name: String,
    val ph: String,
    val ts: Long,
    val pid: Long = 0,
    val tid: Long = 0,
    val cat: String = "",
) {
    companion object {
        const val B = "B" // begin
        const val E = "E" // end
    }
}