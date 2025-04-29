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

import com.google.gson.GsonBuilder
import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import java.io.File

/**
 * @author xcc
 * @date 2025/4/24
 */
internal class GenerateContext(
    mappingFile: File,
    metricsDir: File,
    val logger: Logger,
    val parserList: List<MetricsParser<*>>
) {
    val traceDir = File(metricsDir, "trace")
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()!!
    val metricsFiles = metricsDir.listFiles()?.filter { it.isFile } ?: emptyList()
    val mappingMethod = mappingFile.bufferedReader(Metadata.charset).useLines { lines ->
        lines.map { MethodData.fromOutput(it) }.associateBy { it.id }
    }

    init {
        require(mappingFile.exists()) { "$mappingFile not exists" }
        require(metricsDir.exists()) { "$metricsDir not exists" }
        traceDir.mkdirs()
    }
}