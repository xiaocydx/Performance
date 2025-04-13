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

import com.xiaocydx.performance.plugin.enforcer.MethodInfo.Companion.INITIAL_ID
import groovyjarjarasm.asm.Opcodes
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author xcc
 * @date 2025/4/13
 */
internal abstract class Enforcer {
    private val tasks = mutableListOf<Future<*>>()

    protected fun File.isNeedClassFile(): Boolean {
        if (!isFile) return false
        val name = name
        if (!name.endsWith(".class")) return false
        UN_NEED_CLASS.forEach { if ( name.contains(it)) return false }
        return true
    }

    protected fun addTask(task: Future<*>) {
        tasks.add(task)
    }

    protected fun awaitTasks() {
        tasks.forEach { it.await() }
        tasks.clear()
    }

    private companion object {
        val UN_NEED_CLASS = arrayOf("R.class", "R$", "Manifest", "BuildConfig")
    }
}

internal const val ASM_API = Opcodes.ASM9

internal fun <R> Future<R>.await() = get()

internal data class MethodInfo(
    val id: Int,
    val access: Int,
    val className: String,
    val methodName: String
) {

    fun toKey(): String {
        return key(className, methodName)
    }

    fun toOutput(): String {
        return "${id}${DELIMITER}${access}${DELIMITER}${className}${DELIMITER}$methodName"
    }

    companion object {
        private const val DELIMITER = ','
        const val INITIAL_ID = 0
        val charset = Charsets.UTF_8

        fun key(className: String, methodName: String): String {
            return "${className}.$methodName"
        }

        fun fromOutput(output: String): MethodInfo {
            val property = output.split(DELIMITER)
            val id = property[0].toInt()
            val access = property[1].toInt()
            val className = property[2]
            val name = property[3]
            return MethodInfo(id, access, className, name)
        }
    }
}

internal class IdGenerator(initial: Int = INITIAL_ID) {
    private val id = AtomicInteger(initial)

    fun generate() = id.incrementAndGet()
}