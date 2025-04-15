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

import android.content.Context
import android.os.SystemClock
import com.xiaocydx.performance.analyzer.block.BlockSnapshotReceiver.Companion.DEFAULT_THRESHOLD_MILLIS
import com.xiaocydx.performance.runtime.history.Snapshot
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @author xcc
 * @date 2025/4/15
 */
class BlockSnapshotWriter(
    context: Context,
    override val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS,
) : BlockSnapshotReceiver {
    private val context = requireNotNull(context.applicationContext)

    override fun onReceive(scene: String, snapshot: Snapshot) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val blockDir = File(context.filesDir, "performance/block")
            blockDir.takeIf { !it.exists() }?.mkdirs()
            val file = File(blockDir, "${SystemClock.uptimeMillis()}-snapshot.txt")
            file.printWriter().use { writer ->
                for (i in 0 until snapshot.size) {
                    writer.println(snapshot.valueAt(i))
                }
            }
        }
    }
}