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
import com.google.gson.GsonBuilder
import com.xiaocydx.performance.analyzer.`tag_createTime`
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.File.separator
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 往设备写入[BlockMetrics]
 *
 * @author xcc
 * @date 2025/4/15
 */
class BlockMetricsWriter(private val application: Application) : BlockMetricsReceiver {
    private val gson by lazy { GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create() }

    override fun receive(metrics: BlockMetrics) {
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            val json = gson.toJson(metrics)
            file(metrics).bufferedWriter().use { it.write(json) }
        }
    }

    private fun file(metrics: BlockMetrics): File {
        val name = metrics.tag_createTime()
        val child = "performance${separator}block${separator}${name}"
        val file = File(application.filesDir, child)
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()
        file.takeIf { !it.exists() }?.delete()
        return file
    }
}