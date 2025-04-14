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
import org.objectweb.asm.commons.AdviceAdapter
import java.io.InputStream
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class ModifyEnforcer(
    private val dispatcher: Dispatcher,
    private val output: OutputEnforcer,
) : AbstractEnforcer() {

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
        collectResult: CollectResult,
    ) {
        val tasks = TaskCountDownLatch()

        inputJars.get().forEach { jar ->
            dispatcher.execute(tasks) {
                val jarFile = JarFile(jar.asFile)
                jarFile.entries().iterator().forEach action@{ entry ->
                    if (!entry.isModifiableClass()) {
                        output.write(jarFile, entry)
                        return@action
                    }
                    val time = measureTimeMillis {
                        val bytes = modify(jarFile.getInputStream(entry), collectResult)
                        output.write(entry.name, bytes)
                    }
                    println("Modify from jar ${entry.name} ${time}ms")
                }
                output.close(jarFile)
            }
        }

        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                val name = outputName(directory, file)
                if (!file.isFile || !file.isModifiableClass()) {
                    output.write(name, file)
                    return@action
                }
                dispatcher.execute(tasks) {
                    val time = measureTimeMillis {
                        val bytes = modify(file.inputStream(), collectResult)
                        output.write(name, bytes)
                    }
                    println("Modify from directory $name ${time}ms")
                }
            }
        }

        tasks.await()
    }

    private fun modify(inputStream: InputStream, collectResult: CollectResult): ByteArray {
        return inputStream.use {
            val classReader = ClassReader(it)
            val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
            val classVisitor = ModifyClassVisitor(ASM_API, classWriter, collectResult)
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            classWriter.toByteArray()
        }
    }

    private inner class ModifyClassVisitor(
        api: Int,
        classVisitor: ClassVisitor,
        private val collectResult: CollectResult,
    ) : ClassVisitor(api, classVisitor) {
        private var className = ""
        private var isModifiable = false

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            className = name ?: ""
            isModifiable = isModifiableClass(access)
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
                !isModifiable || methodInfo == null -> methodVisitor
                else -> ModifyMethodVisitor(api, methodVisitor, access, name, descriptor, methodInfo)
            }
        }
    }

    private inner class ModifyMethodVisitor(
        api: Int,
        methodVisitor: MethodVisitor?,
        access: Int,
        name: String?,
        descriptor: String?,
        private val methodInfo: MethodInfo,
    ) : AdviceAdapter(api, methodVisitor, access, name, descriptor) {

        override fun onMethodEnter() {
            mv.visitLdcInsn(methodInfo.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, ENTER, DESCRIPTOR, false)
        }

        override fun onMethodExit(opcode: Int) {
            mv.visitLdcInsn(methodInfo.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, EXIT, DESCRIPTOR, false)
        }
    }

    private companion object {
        const val HISTORY = "com/xiaocydx/performance/runtime/history/History"
        const val ENTER = "enter"
        const val EXIT = "exit"
        const val DESCRIPTOR = "(I)V"
    }
}