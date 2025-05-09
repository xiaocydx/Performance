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

package com.xiaocydx.performance.runtime.history

import android.annotation.SuppressLint
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.BytecodeApi
import com.xiaocydx.performance.runtime.history.record.Record.Companion.ID_MAX
import com.xiaocydx.performance.runtime.history.record.Record.Companion.ID_SLICE
import com.xiaocydx.performance.runtime.history.record.Recorder
import com.xiaocydx.performance.runtime.history.record.Snapshot
import com.xiaocydx.performance.runtime.history.sample.Sampler
import com.xiaocydx.performance.runtime.history.segment.Merger

/**
 * @author xcc
 * @date 2025/4/8
 */
internal object History {
    private val mainThreadId = Looper.getMainLooper().thread.id
    private var isInitialized = false
    private lateinit var recorder: Recorder

    @Volatile
    private var isRecorderCreated = false

    @get:MainThread
    var isTraceEnabled = false; private set

    @get:MainThread
    var isRecordEnabled = false; private set

    const val NO_MARK = -1L

    @MainThread
    fun init() {
        assert(isMainThread())
        isInitialized = true
    }

    @JvmStatic
    @BytecodeApi
    @SuppressLint("UnclosedTrace")
    fun beginTrace(name: String) {
        if (!isMainThread() || !isInitialized) return
        isTraceEnabled = true
        Trace.beginSection(name)
    }

    @JvmStatic
    @BytecodeApi
    fun endTrace() {
        if (!isMainThread() || !isInitialized) return
        // 判断isTraceEnabled，排除调用顺序：
        // beginTrace() -> init() -> endTrace()
        if (!isTraceEnabled) return
        Trace.endSection()
    }

    @JvmStatic
    @BytecodeApi
    fun enter(id: Int) {
        if (id >= ID_MAX) return
        if (!isMainThread() || !isInitialized) return
        if (!isRecordEnabled) {
            createRecorder()
            isRecordEnabled = true
            // volatile write: release (Safe Publication)
            isRecorderCreated = true
        }
        recorder.enter(id, currentMs())
    }

    @JvmStatic
    @BytecodeApi
    fun exit(id: Int) {
        if (id >= ID_MAX) return
        if (!isMainThread() || !isInitialized) return
        // 判断isRecordEnabled，排除调用顺序：
        // enter() -> init() -> exit()
        if (!isRecordEnabled) return
        recorder.exit(id, currentMs())
    }

    @MainThread
    fun startMark(uptimeMillis: Long): Long {
        // 频繁调用不做MainThread断言，流程确保MainSafe
        if (!isInitialized || !isRecordEnabled) return NO_MARK
        recorder.enter(id = ID_SLICE, uptimeMillis)
        return recorder.mark()
    }

    @MainThread
    fun endMark(uptimeMillis: Long): Long {
        // 频繁调用不做MainThread断言，流程确保MainSafe
        if (!isInitialized || !isRecordEnabled) return NO_MARK
        recorder.exit(id = ID_SLICE, uptimeMillis)
        return recorder.mark()
    }

    @AnyThread
    fun latestMark(): Long {
        if (isRecorderCreated) {
            // volatile read: acquire (Safe Publication)
            return recorder.mark()
        }
        return NO_MARK
    }

    @AnyThread
    fun snapshot(startMark: Long, endMark: Long): Snapshot {
        return when {
            startMark < 0 || endMark < 0 -> Snapshot.empty()
            // volatile read: acquire (Safe Publication)
            !isRecorderCreated -> Snapshot.empty()
            else -> recorder.snapshot(startMark, endMark)
        }
    }

    @MainThread
    fun merger(idleThresholdMillis: Long, mergeThresholdMillis: Long): Merger? {
        assert(isMainThread())
        if (!isInitialized) return null
        return Merger(capacity = 200, idleThresholdMillis, mergeThresholdMillis)
    }

    @MainThread
    fun sampler(looper: Looper, intervalMillis: Long): Sampler? {
        assert(isMainThread())
        if (!isInitialized) return null
        return Sampler(capacity = 200, looper = looper, intervalMillis = intervalMillis)
    }

    @SuppressLint("UnclosedTrace")
    private fun createRecorder() {
        Trace.beginSection("HistoryCreateRecorder")
        recorder = Recorder(capacity = 100 * 10000)
        Trace.endSection()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isMainThread(): Boolean {
        return Thread.currentThread().id == mainThreadId
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun currentMs(): Long {
        // 调用一次耗时是2000 ~ 3000ns，
        // 调用至少333次才产生ms级的影响，
        // 这个影响可接受，暂时不用采样方案。
        // 采样做volatile读，需评估性能损耗。
        return SystemClock.uptimeMillis()
    }
}