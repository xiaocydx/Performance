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
import com.xiaocydx.performance.plugin.enforcer.MethodInfo.Companion.INITIAL_ID
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import java.util.concurrent.ConcurrentHashMap

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class CollectEnforcer(
    private val dispatcher: Dispatcher,
    private val idGenerator: IdGenerator
) : Enforcer() {
    private val ignored = ConcurrentHashMap<String, MethodInfo>()
    private val handled = ConcurrentHashMap<String, MethodInfo>()

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
    ): CollectResult {
        // TODO: 对Jar解包，遍历，通过ASM判断是否处理
        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                if (!file.isNeedClassFile()) return@action
                addTask(dispatcher.submit {
                    file.inputStream().use {
                        val classReader = ClassReader(it)
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                        val classVisitor = CollectClassVisitor(ASM_API, classWriter)
                        try {
                            classReader.accept(classVisitor, 0)
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                })
            }
        }
        awaitTasks()
        return CollectResult(ignored, handled)
    }

    private inner class CollectClassVisitor(
        api: Int,
        classVisitor: ClassVisitor
    ) : ClassVisitor(api, classVisitor) {
        private var className = ""
        private var isSkip = false

        override fun visit(
            version: Int,
            access: Int, name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            // TODO: 跟Modify统一跳过Class的条件
            className = name ?: ""
            isSkip = access and Opcodes.ACC_INTERFACE != 0 || access and Opcodes.ACC_ABSTRACT != 0
        }

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor = if (isSkip) {
            super.visitMethod(access, name, descriptor, signature, exceptions)
        } else {
            CollectMethodNode(ASM_API, access, name, descriptor, signature, exceptions, className)
        }
    }

    private inner class CollectMethodNode(
        api: Int,
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
        private val className: String,
    ) : MethodNode(api, access, name, descriptor, signature, exceptions) {

        override fun visitEnd() {
            super.visitEnd()
            val methodKey = MethodInfo.key(className, name)
            // TODO: 替换为过滤条件
            val isTestClass = className.endsWith("PerformanceTest")
            if (!isTestClass || isEmptyMethod() || isGetSetMethod() || isSingleMethod()) {
                ignored[methodKey] = MethodInfo(
                    id = INITIAL_ID, access = access,
                    className = className, methodName = name
                )
            } else {
                handled[methodKey] = MethodInfo(
                    id = idGenerator.generate(), access = access,
                    className = className, methodName = name
                )
            }
        }

        private fun isEmptyMethod(): Boolean {
            val isConstructor = name == "<init>"
            val instructions = requireNotNull(instructions)
            var skipCount = 0
            instructions.forEach {
                val opcode = it.opcode
                if (opcode == -1) return@forEach
                if (isConstructor && opcode == Opcodes.INVOKESPECIAL) {
                    // FIXME:  ALOAD是第一个
                    // 子类构造函数调用父类构造函数
                    skipCount++
                    if (skipCount > 1) return false
                    return@forEach
                }
                return false
            }
            return true
        }

        private fun isGetSetMethod(): Boolean {
            val instructions = requireNotNull(instructions)
            instructions.forEach {
                val opcode = it.opcode
                if (opcode == -1) return@forEach
                if (opcode != Opcodes.GETFIELD
                        && opcode != Opcodes.GETSTATIC
                        && opcode != Opcodes.H_GETFIELD
                        && opcode != Opcodes.H_GETSTATIC
                        && opcode != Opcodes.RETURN
                        && opcode != Opcodes.ARETURN
                        && opcode != Opcodes.DRETURN
                        && opcode != Opcodes.FRETURN
                        && opcode != Opcodes.LRETURN
                        && opcode != Opcodes.IRETURN
                        && opcode != Opcodes.PUTFIELD
                        && opcode != Opcodes.PUTSTATIC
                        && opcode != Opcodes.H_PUTFIELD
                        && opcode != Opcodes.H_PUTSTATIC
                        && opcode > Opcodes.SALOAD) {
                    return false
                }
            }
            return true
        }

        private fun isSingleMethod(): Boolean {
            val instructions = requireNotNull(instructions)
            instructions.forEach { if (it.opcode in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC) return false }
            return true
        }
    }
}

internal data class CollectResult(
    val ignored: Map<String, MethodInfo>,
    val handled: Map<String, MethodInfo>
)