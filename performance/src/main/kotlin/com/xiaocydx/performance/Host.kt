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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.xiaocydx.performance

import android.app.Activity
import android.app.ActivityManager
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.component.ActivityEvent
import com.xiaocydx.performance.runtime.component.ActivityKey
import com.xiaocydx.performance.runtime.future.PendingMessage
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.segment.Merger.Range
import com.xiaocydx.performance.runtime.looper.LooperCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * @author xcc
 * @date 2025/4/26
 */
internal interface Host {

    @get:AnyThread
    val pid: Int

    @get:AnyThread
    val dumpLooper: Looper

    @get:AnyThread
    val defaultLooper: Looper

    @get:AnyThread
    val ams: ActivityManager

    @get:AnyThread
    val anrEvent: SharedFlow<Unit>

    @get:AnyThread
    val activityEvent: SharedFlow<ActivityEvent>

    @get:MainThread
    val isRecordEnabled: Boolean

    @AnyThread
    fun createMainScope(): CoroutineScope

    @MainThread
    fun getActivity(key: ActivityKey): Activity?

    @MainThread
    fun getLatestActivity(): Activity?

    @AnyThread
    fun getActiveActivityCount(): Int

    @MainThread
    fun addCallback(callback: LooperCallback)

    @MainThread
    fun removeCallback(callback: LooperCallback)

    @MainThread
    fun registerHistory(token: HistoryToken)

    @MainThread
    fun unregisterHistory(token: HistoryToken)

    @AnyThread
    fun snapshot(startMark: Long, endMark: Long): Snapshot

    @AnyThread
    fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample>

    @AnyThread
    fun sampleImmediately(): Sample?

    @MainThread
    fun segmentRange(startUptimeMillis: Long, endUptimeMillis: Long): List<Range>

    @AnyThread
    fun getFirstPending(uptimeMillis: Long = SystemClock.uptimeMillis()): PendingMessage?

    @AnyThread
    fun getPendingList(uptimeMillis: Long = SystemClock.uptimeMillis()): List<PendingMessage>
}

internal data class HistoryToken(
    val analyzer: Analyzer,
    val needSignal: Boolean = false,
    val needSample: Boolean = false,
    val needSegment: Boolean = false
)

internal class HistoryTokenStore {
    private val map = HashMap<Analyzer, HistoryToken>()
    var prevCount = Count(); private set
    var currCount = prevCount; private set

    inline fun isEmptyToNotEmpty(property: Count.() -> Int): Boolean {
        val prev = prevCount.property()
        val curr = currCount.property()
        return prev == 0 && curr > 0
    }

    inline fun isNotEmptyToEmpty(property: Count.() -> Int): Boolean {
        val prev = prevCount.property()
        val curr = currCount.property()
        return prev > 0 && curr == 0
    }

    fun add(token: HistoryToken): Boolean {
        if (map.containsKey(token.analyzer)) return false
        modify { map[token.analyzer] = token }

        return true
    }

    fun remove(token: HistoryToken): Boolean {
        if (!map.containsKey(token.analyzer)) return false
        modify { map.remove(token.analyzer) }
        return true
    }

    private inline fun <R> modify(action: () -> R): R {
        prevCount = recordCount()
        val result = action()
        currCount = recordCount()
        return result
    }

    private fun recordCount(): Count {
        var needSignal = 0
        var needSample = 0
        var needSegment = 0
        map.values.forEach {
            needSignal += if (it.needSignal) 1 else 0
            needSample += if (it.needSample) 1 else 0
            needSegment += if (it.needSegment) 1 else 0
        }
        return Count(
            total = map.values.size,
            needSignal = needSignal,
            needSample = needSample,
            needSegment = needSegment
        )
    }

    data class Count(
        val total: Int = 0,
        val needSignal: Int = 0,
        val needSample: Int = 0,
        val needSegment: Int = 0
    )
}