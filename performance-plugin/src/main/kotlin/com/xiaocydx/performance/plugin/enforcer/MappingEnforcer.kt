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
    keepMethodFile: String,
    ignoredMethodFile: String,
    handledMethodFile: String,
) : AbstractEnforcer() {
    private val charset = MethodInfo.charset
    private val keepMethodFile = File(keepMethodFile)
    private val ignoredMethodFile = File(ignoredMethodFile)
    private val handledMethodFile = File(handledMethodFile)

    fun read(): MappingResult.Read {
        val keepChecker = readKeep(keepMethodFile)
        // TODO: 实现增量才需要previousHandled
        // val previousHandled = readMapping(handledMethodFile)
        // val idGenerator = when {
        //     previousHandled.isEmpty() -> IdGenerator()
        //     else -> IdGenerator(initial = previousHandled.maxOf { it.id })
        // }
        val previousHandled = emptyList<MethodInfo>()
        val idGenerator = IdGenerator()
        return MappingResult.Read(idGenerator, keepChecker, previousHandled)
    }

    fun submitWrite(result: CollectResult): Future<MappingResult.Write> {
        return dispatcher.submit {
            val ignored = result.ignored.values.toList()
            val handled = result.handled.values.toMutableList()
            handled.sortBy { it.id }
            writeMapping(ignoredMethodFile, ignored)
            writeMapping(handledMethodFile, handled)
            MappingResult.Write(ignored, handled)
        }
    }

    private fun readKeep(file: File): KeepChecker {
        val classSet = mutableSetOf<String>()
        val packageSet = mutableSetOf<String>()
        packageSet.addAll(DEFAULT_KEEP_PACKAGE)
        if (file.exists()) {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith(KEEP_CLASS_PREFIX) -> {
                            classSet.add(line.replace(KEEP_CLASS_PREFIX, ""))
                        }
                        line.startsWith(KEEP_PACKAGE_PREFIX) -> {
                            packageSet.add(line.replace(KEEP_PACKAGE_PREFIX, ""))
                        }
                    }
                }
            }
        }
        return KeepChecker(classSet, packageSet)
    }

    private fun readMapping(file: File): List<MethodInfo> {
        if (!file.exists()) return emptyList()
        val outcome = file.bufferedReader(charset).useLines { lines ->
            lines.map { MethodInfo.fromOutput(it) }.toList()
        }
        return outcome
    }

    private fun writeMapping(file: File, list: List<MethodInfo>) {
        file.parentFile.takeIf { !it.exists() }?.mkdirs()
        file.printWriter(charset).use { writer ->
            list.forEach { writer.println(it.toOutput()) }
        }
    }

    private companion object {
        const val KEEP_CLASS_PREFIX = "-keepclass "
        const val KEEP_PACKAGE_PREFIX = "-keeppackage "
        val DEFAULT_KEEP_PACKAGE = listOf("android/", "com/xiaocydx/performance/")
    }
}

internal sealed class MappingResult {
    data class Read(
        val idGenerator: IdGenerator,
        val keepChecker: KeepChecker,
        val previousHandled: List<MethodInfo>,
    ) : MappingResult()

    data class Write(
        val currentIgnored: List<MethodInfo>,
        val currentHandled: List<MethodInfo>,
    ) : MappingResult()
}

internal class KeepChecker(
    private val classSet: Set<String>,
    private val packageSet: Set<String>,
) {

    fun isKeepClass(className: String): Boolean {
        if (classSet.contains(className)) return true
        val last = className.lastIndexOf('/')
        val end = (last + 1).coerceAtMost(className.length)
        val packageName = className.substring(0, end)
        packageSet.forEach { if (packageName.startsWith(it)) return true }
        return false
    }
}