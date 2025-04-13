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
import java.io.File
import java.util.concurrent.Future

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class MappingEnforcer(
    private val dispatcher: Dispatcher,
    ignoredMethodFile: String,
    handledMethodFile: String,
) : Enforcer() {
    private val charset = MethodInfo.charset
    private val ignoredMethodFile = File(ignoredMethodFile)
    private val handledMethodFile = File(handledMethodFile)

    fun submitRead(): Future<ReadResult> {
        return dispatcher.submit {
            val previousHandled = read(handledMethodFile)
            val idGenerator = when {
                previousHandled.isEmpty() -> IdGenerator()
                else -> IdGenerator(initial = previousHandled.maxOf { it.id })
            }
            ReadResult(idGenerator, previousHandled)
        }
    }

    fun submitWrite(result: CollectResult): Future<WriteResult> {
        return dispatcher.submit {
            val ignored = result.ignored.values.toMutableList()
            val handled = result.handled.values.toMutableList()
            ignored.sortBy { it.id }
            handled.sortBy { it.id }
            write(ignoredMethodFile, ignored)
            write(handledMethodFile, handled)
            WriteResult(ignored, handled)
        }
    }

    private fun read(file: File): List<MethodInfo> {
        if (!file.exists()) return emptyList()
        val outcome = file.bufferedReader(charset).useLines { lines ->
            lines.map { MethodInfo.fromOutput(it) }.toList()
        }
        return outcome
    }

    private fun write(file: File, list: List<MethodInfo>) {
        file.parentFile.takeIf { !it.exists() }?.mkdirs()
        file.printWriter(charset).use { writer ->
            list.forEach { writer.println(it.toOutput()) }
        }
    }

    data class ReadResult(
        val idGenerator: IdGenerator,
        val previousHandled: List<MethodInfo>
    )

    data class WriteResult(
        val currentIgnored: List<MethodInfo>,
        val currentHandled: List<MethodInfo>
    )
}