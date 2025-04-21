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

package com.xiaocydx.performance.analyzer.block

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.xiaocydx.performance.analyzer.block.BlockReceiver.Companion.DEFAULT_THRESHOLD_MILLIS
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author xcc
 * @date 2025/4/15
 */
class BlockWriter(
    private val application: Application,
    override val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS,
) : BlockReceiver {

    override fun onBlock(report: BlockReport) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            print(report)
            write(report)
        }
    }

    private fun print(report: BlockReport) {
        val json = JSONObject().apply {
            put("scene", report.scene)
            put("lastActivity", report.lastActivity)
            put("priority", report.priority)
            put("nice", report.nice)
            put("thresholdMillis", report.thresholdMillis)
            put("wallDurationMillis", report.wallDurationMillis)
            put("cpuDurationMillis", report.cpuDurationMillis)
            put("isRecordEnabled", report.isRecordEnabled)
            put("snapshotAvailable", report.snapshot.isAvailable)
            put("value", report.value)
        }
        Log.e(TAG, json.toString(2))
    }

    private fun write(report: BlockReport) {
        val snapshot = report.snapshot
        if (!snapshot.isAvailable) return
        val blockDir = File(application.filesDir, "performance/block")
        blockDir.takeIf { !it.exists() }?.mkdirs()
        val file = File(blockDir, "${SystemClock.uptimeMillis()}-snapshot.txt")
        file.printWriter().use { writer ->
            for (i in 0 until snapshot.size) {
                writer.println(snapshot[i].value)
            }
        }
    }

    private companion object {
        const val TAG = "BlockWriter"
    }
}