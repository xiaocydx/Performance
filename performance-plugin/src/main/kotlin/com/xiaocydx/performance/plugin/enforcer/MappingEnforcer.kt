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

package com.xiaocydx.performance.plugin.enforcer

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.ClassData
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Metadata
import com.xiaocydx.performance.plugin.metadata.MethodData
import java.io.File
import java.util.concurrent.Future

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class MappingEnforcer(
    private val dispatcher: Dispatcher,
    keepMethodFile: String,
    ignoredClassFile: String,
    ignoredMethodFile: String,
    mappingMethodFile: String,
) : AbstractEnforcer() {
    private val keepMethodFile = File(keepMethodFile)
    private val ignoredClassFile = File(ignoredClassFile)
    private val ignoredMethodFile = File(ignoredMethodFile)
    private val mappingMethodFile = File(mappingMethodFile)

    fun read(): MappingResult.Read {
        // TODO: 实现增量才需要mappingMethod
        // val mappingMethod = readMethod(mappingMethodFile)
        // val idGenerator = when {
        //     mappingMethod.isEmpty() -> IdGenerator()
        //     else -> IdGenerator(initial = mappingMethod.maxOf { it.id })
        // }
        val inspector = Inspector.create(keepMethodFile)
        val idGenerator = IdGenerator()
        val mappingMethod = emptyList<MethodData>()
        return MappingResult.Read(inspector, idGenerator, mappingMethod)
    }

    fun submitWrite(result: CollectResult): Future<MappingResult.Write> {
        return dispatcher.submit {
            val ignoredClass = result.ignoredClass.values.toList()
            val ignoredMethod = result.ignoredMethod.values.toList()
            val mappingMethod = result.mappingMethod.values.sortedBy { it.id }
            write(ignoredClassFile, ignoredClass)
            write(ignoredMethodFile, ignoredMethod)
            write(mappingMethodFile, mappingMethod)
            MappingResult.Write(ignoredClass, ignoredMethod, mappingMethod)
        }
    }

    private fun readMethod(file: File): List<MethodData> {
        if (!file.exists()) return emptyList()
        val outcome = file.bufferedReader(Metadata.charset).useLines { lines ->
            lines.map { MethodData.fromOutput(it) }.toList()
        }
        return outcome
    }

    private fun write(file: File, list: List<Metadata>) {
        file.parentFile.takeIf { !it.exists() }?.mkdirs()
        file.printWriter(Metadata.charset).use { writer ->
            list.forEach { writer.println(it.toOutput()) }
        }
    }
}

internal sealed class MappingResult {
    data class Read(
        val inspector: Inspector,
        val idGenerator: IdGenerator,
        val mappingMethod: List<MethodData>,
    ) : MappingResult()

    data class Write(
        val ignoredClass: List<ClassData>,
        val ignoredMethod: List<MethodData>,
        val mappingMethod: List<MethodData>,
    ) : MappingResult()
}