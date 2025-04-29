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

package com.xiaocydx.performance.plugin.generate

/**
 * [Trace Event Format](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview)
 *
 * @author xcc
 * @date 2025/4/24
 */
internal class TraceEvent private constructor(
    val name: String,
    val ph: String,
    val ts: Long,
    val pid: String,
    val tid: String,
    val dur: Long? = null,
    val cat: String? = null,
    val args: Any? = null
) {

    companion object {

        fun ts(millis: Long): Long {
            return millis * 1000
        }

        fun pid(pid: Int): String {
            return "Process [pid = ${pid}]"
        }

        fun tid(tid: Int): String {
            return "Thread [tid = ${tid}]"
        }

        /**
         * [Duration Events](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview?tab=t.0#heading=h.nso4gcezn7n1)
         */
        fun duration(
            name: String,
            isBegin: Boolean,
            ts: Long,
            pid: String,
            tid: String,
            cat: String? = null,
            args: Any? = null
        ) = TraceEvent(
            name = name,
            ph = if (isBegin) "B" else "E",
            ts = ts,
            pid = pid,
            tid = tid,
            cat = cat,
            args = args
        )

        /**
         * [Complete Events](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview?tab=t.0#heading=h.lpfof2aylapb)
         */
        fun complete(
            name: String,
            startTs: Long,
            endTs: Long,
            pid: String,
            tid: String,
            cat: String? = null,
            args: Any? = null
        ) = TraceEvent(
            name = name,
            ph = "X",
            ts = startTs,
            pid = pid,
            tid = tid,
            dur = endTs - startTs,
            cat = cat,
            args = args,
        )

        /**
         * [Instant Events](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview?tab=t.0#heading=h.lenwiilchoxp)
         */
        fun instant(
            name: String,
            ts: Long,
            pid: String,
            tid: String,
            cat: String? = null,
            args: Any? = null
        ) = TraceEvent(
            name = name,
            ph = "i",
            ts = ts,
            pid = pid,
            tid = tid,
            cat = cat,
            args = args
        )
    }
}