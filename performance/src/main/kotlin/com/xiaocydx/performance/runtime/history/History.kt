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

@file:Suppress("NOTHING_TO_INLINE")

package com.xiaocydx.performance.runtime.history

import android.annotation.SuppressLint
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.BytecodeApi
import com.xiaocydx.performance.runtime.history.Record.Companion.ID_MAX
import com.xiaocydx.performance.runtime.history.Record.Companion.ID_SLICE

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
            // volatile写，release recorder != null
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
    fun startMark(): Long {
        // 频繁调用不做MainThread断言，流程确保MainSafe
        if (!isInitialized || !isRecordEnabled) return NO_MARK
        recorder.enter(id = ID_SLICE, currentMs())
        return recorder.mark()
    }

    @MainThread
    fun endMark(): Long {
        // 频繁调用不做MainThread断言，流程确保MainSafe
        if (!isInitialized || !isRecordEnabled) return NO_MARK
        recorder.exit(id = ID_SLICE, currentMs())
        return recorder.mark()
    }

    @AnyThread
    fun latestMark(): Long {
        // volatile读，acquire recorder != null
        if (!isRecorderCreated) return NO_MARK
        return recorder.mark()
    }

    @AnyThread
    fun snapshot(startMark: Long, endMark: Long): Snapshot {
        return when {
            startMark < 0 || endMark < 0 -> Snapshot(longArrayOf())
            // volatile读，acquire recorder != null
            !isRecorderCreated -> Snapshot(longArrayOf())
            // 短时间内[startMark, endMark]的数据不被覆盖，可视为不可变。
            // 当调用snapshot(startMark, latestMark())时，不稳定的结果：
            // 1. latestMark()未读到最新值，buffer未读到最新值，可接受的结果。
            // 2. latestMark()已读到最新值，buffer未读到最新值，需进一步过滤。
            // recorder.latestMark不用volatile，recorder.record()会频繁调用。
            else -> recorder.snapshot(startMark, endMark)
        }
    }

    fun segmentChain(idleThresholdMillis: Long, mergeThresholdMillis: Long): SegmentChain? {
        if (!isInitialized) return null
        return SegmentChain(capacity = 5 * 1000, idleThresholdMillis, mergeThresholdMillis)
    }

    @SuppressLint("UnclosedTrace")
    private fun createRecorder() {
        Trace.beginSection("HistoryCreateRecorder")
        recorder = Recorder(capacity = 100 * 10000)
        Trace.endSection()
    }

    private inline fun isMainThread(): Boolean {
        return Thread.currentThread().id == mainThreadId
    }

    private inline fun currentMs(): Long {
        // 调用一次耗时是2000 ~ 3000ns，
        // 调用至少333次才产生ms级的影响，
        // 这个影响可接受，暂时不用采样方案。
        // 采样做volatile读，需评估性能损耗。
        return SystemClock.uptimeMillis()
    }
}