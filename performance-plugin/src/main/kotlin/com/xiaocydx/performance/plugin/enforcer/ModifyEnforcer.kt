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
import com.xiaocydx.performance.plugin.metadata.MethodData
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
    private val inspector: Inspector,
    private val isTraceEnabled: Boolean,
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
                    if (!inspector.isClass(entry)) return@action
                    val className = inspector.className(entry)
                    val outputName = entry.name
                    if (collectResult.isIgnoredClass(className)) {
                        output.write(jarFile, entry)
                        return@action
                    }
                    val time = measureTimeMillis {
                        val bytes = modify(jarFile.getInputStream(entry), collectResult)
                        output.write(outputName, bytes)
                    }
                    println("Modify from jar $outputName ${time}ms")
                }
                output.close(jarFile)
            }
        }

        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                if (!inspector.isClass(file)) return@action
                val className = inspector.className(directory, file)
                val outputName = inspector.outputName(directory, file)
                if (collectResult.isIgnoredClass(className)) {
                    output.write(outputName, file)
                    return@action
                }
                dispatcher.execute(tasks) {
                    val time = measureTimeMillis {
                        val bytes = modify(file.inputStream(), collectResult)
                        output.write(outputName, bytes)
                    }
                    println("Modify from directory $outputName ${time}ms")
                }
            }
        }

        tasks.await()
    }

    private fun modify(inputStream: InputStream, collectResult: CollectResult): ByteArray {
        return inputStream.use {
            val classReader = ClassReader(it)
            val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
            val classVisitor = ModifyClassVisitor(classWriter, collectResult)
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            classWriter.toByteArray()
        }
    }

    private inner class ModifyClassVisitor(
        classVisitor: ClassVisitor,
        private val collectResult: CollectResult,
    ) : ClassVisitor(ASM_API, classVisitor) {
        private var className = ""
        private var isModifiable = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            className = name
            isModifiable = inspector.isModifiable(access)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            val methodData = collectResult.mappingMethod[MethodData.key(className, name, descriptor)]
            return when {
                !isModifiable || methodData == null -> methodVisitor
                else -> ModifyMethodVisitor(methodVisitor, access, name, descriptor, methodData)
            }
        }
    }

    private inner class ModifyMethodVisitor(
        methodVisitor: MethodVisitor?,
        access: Int,
        name: String?,
        descriptor: String?,
        private val methodData: MethodData,
    ) : AdviceAdapter(ASM_API, methodVisitor, access, name, descriptor) {
        private val prettyName: String

        init {
            if (isTraceEnabled) {
                val className = methodData.className.replace("/", ".")
                prettyName = "${className}.${methodData.methodName}"
            } else {
                prettyName = ""
            }
        }

        override fun onMethodEnter() {
            if (isTraceEnabled) {
                mv.visitLdcInsn(prettyName)
                mv.visitMethodInsn(INVOKESTATIC, HISTORY, "beginTrace", "(Ljava/lang/String;)V", false)
            }
            mv.visitLdcInsn(methodData.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, "enter", "(I)V", false)
        }

        override fun onMethodExit(opcode: Int) {
            mv.visitLdcInsn(methodData.id)
            mv.visitMethodInsn(INVOKESTATIC, HISTORY, "exit", "(I)V", false)
            if (isTraceEnabled) {
                mv.visitMethodInsn(INVOKESTATIC, HISTORY, "endTrace", "()V", false)
            }
        }
    }

    private companion object {
        const val HISTORY = "com/xiaocydx/performance/runtime/history/History"
    }
}