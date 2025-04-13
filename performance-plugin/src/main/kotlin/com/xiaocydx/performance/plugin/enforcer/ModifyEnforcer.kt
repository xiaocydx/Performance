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
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter
import java.io.File

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class ModifyEnforcer(
    private val dispatcher: Dispatcher,
    private val output: OutputEnforcer,
) : Enforcer() {

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
        collectResult: CollectResult
    ) {
        output.write(inputJars)
        directories.get().forEach { directory ->
            println("handling " + directory.asFile.absolutePath)
            directory.asFile.walk().forEach action@{ file ->
                if (!file.isNeedClassFile()) return@action
                val name = name(relativePath(directory, file))
                println("Adding from directory $name")
                addTask(dispatcher.submit {
                    val bytes = file.inputStream().use {
                        val classReader = ClassReader(it)
                        val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                        val classVisitor = ModifyClassVisitor(ASM_API, classWriter, collectResult)
                        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                        classWriter.toByteArray()
                    }
                    output.write(name, bytes)
                })
            }
        }
        awaitTasks()
    }

    private fun relativePath(directory: Directory, file: File): String {
        return directory.asFile.toURI().relativize(file.toURI()).path
    }

    private fun name(relativePath: String): String {
        return relativePath.replace(File.separatorChar, '/')
    }

    private class ModifyClassVisitor(
        api: Int,
        classVisitor: ClassVisitor,
        private val collectResult: CollectResult
    ) : ClassVisitor(api, classVisitor) {
        private var className = ""
        private var isSkip = false

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            // TODO: 跟collect统一跳过Class的条件
            className = name ?: ""
            isSkip = access and Opcodes.ACC_INTERFACE != 0 || access and Opcodes.ACC_ABSTRACT != 0
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            val methodInfo = collectResult.handled[MethodInfo.key(className, name)]
            return when {
                isSkip || methodInfo == null -> methodVisitor
                else -> ModifyMethodVisitor(api, methodVisitor, access, name, descriptor, methodInfo)
            }
        }
    }

    internal class ModifyMethodVisitor(
        api: Int,
        methodVisitor: MethodVisitor?,
        access: Int,
        name: String?,
        descriptor: String?,
        private val methodInfo: MethodInfo
    ) : AdviceAdapter(api, methodVisitor, access, name, descriptor) {

        override fun onMethodEnter() {
            mv.visitLdcInsn(methodInfo.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, ENTER, DESCRIPTOR, false)
        }

        override fun onMethodExit(opcode: Int) {
            mv.visitLdcInsn(methodInfo.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, EXIT, DESCRIPTOR, false)
        }

        private companion object {
            const val HISTORY = "com/xiaocydx/performance/runtime/history/History"
            const val ENTER = "enter"
            const val EXIT = "exit"
            const val DESCRIPTOR = "(I)V"
        }
    }
}