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

@file:Suppress("UnusedReceiverParameter", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.xiaocydx.performance.plugin.enforcer

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import com.xiaocydx.performance.plugin.enforcer.MethodInfo.Companion.INITIAL_ID
import org.gradle.api.file.Directory
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry

/**
 * @author xcc
 * @date 2025/4/13
 */
internal abstract class AbstractEnforcer {

    protected fun File.isModifiableClass(): Boolean {
        val name = name ?: ""
        if (!name.endsWith(".class")) return false
        UN_NEED_CLASS.forEach { if (name.contains(it)) return false }
        return true
    }

    protected fun JarEntry.isModifiableClass(): Boolean {
        val name = name ?: ""
        if (!name.endsWith(".class")) return false
        UN_NEED_CLASS.forEach { if (name.contains(it)) return false }
        return true
    }

    protected fun ClassVisitor.isModifiableClass(access: Int): Boolean {
        return access and Opcodes.ACC_INTERFACE == 0 && access and Opcodes.ACC_ABSTRACT == 0
    }

    protected fun outputName(directory: Directory, file: File): String {
        return relativePath(directory, file).replace(File.separatorChar, '/')
    }

    protected fun relativePath(directory: Directory, file: File): String {
        return directory.asFile.toURI().relativize(file.toURI()).path
    }

    protected inline fun Dispatcher.execute(
        tasks: TaskCountDownLatch,
        crossinline task: () -> Unit,
    ) {
        tasks.increment()
        execute {
            task()
            tasks.decrement()
        }
    }

    protected class TaskCountDownLatch {
        private val count = AtomicInteger()
        private val lock = this as Object

        fun increment() {
            count.incrementAndGet()
        }

        fun decrement() {
            val count = count.decrementAndGet()
            require(count >= 0) { "计数异常" }
            if (count == 0) {
                synchronized(lock) {
                    lock.notifyAll()
                }
            }
        }

        fun await() {
            synchronized(lock) {
                while (count.get() > 0) {
                    lock.wait()
                }
            }
        }
    }

    protected companion object {
        private val UN_NEED_CLASS = listOf("R.class", "R$", "Manifest", "BuildConfig")
        const val ASM_API = Opcodes.ASM9
    }
}

internal fun <R> Future<R>.await() = get()

internal data class MethodInfo(
    val id: Int,
    val access: Int,
    val className: String,
    val methodName: String,
    val desc: String,
) {

    fun toKey(): String {
        return key(className, methodName, desc)
    }

    fun toOutput(): String {
        return "${id}${DELIMITER}${access}${DELIMITER}" +
                "${className}${DELIMITER}$methodName${DELIMITER}${desc}"
    }

    companion object {
        private const val DELIMITER = ','
        const val INITIAL_ID = 0
        val charset = Charsets.UTF_8

        fun key(className: String, methodName: String, desc: String): String {
            return "${className}${DELIMITER}${methodName}${DELIMITER}$desc"
        }

        fun fromOutput(output: String): MethodInfo {
            val property = output.split(DELIMITER)
            val id = property[0].toInt()
            val access = property[1].toInt()
            val className = property[2]
            val name = property[3]
            val desc = property[4]
            return MethodInfo(id, access, className, name, desc)
        }
    }
}

internal class IdGenerator(initial: Int = INITIAL_ID) {
    private val id = AtomicInteger(initial)

    fun generate() = id.incrementAndGet()
}