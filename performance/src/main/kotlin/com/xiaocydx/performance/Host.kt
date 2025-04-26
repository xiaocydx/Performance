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
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.activity.ActivityEvent
import com.xiaocydx.performance.runtime.activity.ActivityKey
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.history.segment.Merger
import com.xiaocydx.performance.runtime.looper.LooperCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * @author xcc
 * @date 2025/4/26
 */
internal interface Host {

    @get:AnyThread
    val dumpLooper: Looper

    @get:AnyThread
    val defaultLooper: Looper

    @get:AnyThread
    val ams: ActivityManager

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

    @MainThread
    fun addCallback(callback: LooperCallback)

    @MainThread
    fun removeCallback(callback: LooperCallback)

    @MainThread
    fun registerHistory(analyzer: Analyzer)

    @MainThread
    fun unregisterHistory(analyzer: Analyzer)

    @MainThread
    fun merger(idleThresholdMillis: Long, mergeThresholdMillis: Long): Merger

    @AnyThread
    fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample>

    @AnyThread
    fun snapshot(startMark: Long, endMark: Long): Snapshot
}