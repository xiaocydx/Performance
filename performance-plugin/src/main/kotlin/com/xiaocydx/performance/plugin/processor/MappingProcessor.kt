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

package com.xiaocydx.performance.plugin.processor

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import com.xiaocydx.performance.plugin.metadata.writeTo
import java.io.File
import java.util.concurrent.Future

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class MappingProcessor(
    private val dispatcher: Dispatcher,
    excludeManifest: String,
    excludeClassFile: String,
    excludeMethodFile: String,
    mappingMethodFile: String,
) : AbstractProcessor() {
    private val excludeManifest = File(excludeManifest)
    private val excludeClassFile = File(excludeClassFile)
    private val excludeMethodFile = File(excludeMethodFile)
    private val mappingMethodFile = File(mappingMethodFile)

    fun read(): MappingResult {
        // TODO: 实现增量才需要mappingMethod
        // val mappingMethod = readMethod(mappingMethodFile)
        // val idGenerator = when {
        //     mappingMethod.isEmpty() -> IdGenerator()
        //     else -> IdGenerator(initial = mappingMethod.maxOf { it.id })
        // }
        val inspector = Inspector.create(excludeManifest)
        val idGenerator = IdGenerator()
        val mappingMethod = emptyList<MethodData>()
        return MappingResult(inspector, idGenerator, mappingMethod)
    }

    fun submitWrite(result: CollectResult): Future<Unit> {
        return dispatcher.submit {
            result.excludeClass.values.toList().writeTo(excludeClassFile)
            result.excludeMethod.values.toList().writeTo(excludeMethodFile)
            result.mappingMethod.values.sortedBy { it.id }.writeTo(mappingMethodFile)
        }
    }

    private fun readMethod(file: File): List<MethodData> {
        if (!file.exists()) return emptyList()
        val outcome = file.bufferedReader(Metadata.charset).useLines { lines ->
            lines.map { MethodData.fromOutput(it) }.toList()
        }
        return outcome
    }
}

internal data class MappingResult(
    val inspector: Inspector,
    val idGenerator: IdGenerator,
    val mappingMethod: List<MethodData>,
)