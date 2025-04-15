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
import com.xiaocydx.performance.plugin.metadata.ClassData
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.metadata.Metadata.Companion.INITIAL_ID
import com.xiaocydx.performance.plugin.metadata.MethodData
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class CollectEnforcer(
    private val dispatcher: Dispatcher,
    private val idGenerator: IdGenerator,
    private val inspector: Inspector,
) : AbstractEnforcer() {
    private val ignoredClass = ConcurrentHashMap<String, ClassData>()
    private val ignoredMethod = ConcurrentHashMap<String, MethodData>()
    private val mappingMethod = ConcurrentHashMap<String, MethodData>()

    fun await(
        inputJars: ListProperty<RegularFile>,
        directories: ListProperty<Directory>,
    ): CollectResult {
        val tasks = TaskCountDownLatch()

        inputJars.get().forEach { jar ->
            dispatcher.execute(tasks) {
                val jarFile = JarFile(jar.asFile)
                jarFile.entries().iterator().forEach action@{ entry ->
                    if (!inspector.isClass(entry)) return@action
                    inspector.toIgnoredClass(entry)?.let {
                        ignoredClass[ClassData.key(it)] = ClassData(it)
                        return@action
                    }
                    val time = measureTimeMillis { collect(jarFile.getInputStream(entry)) }
                    println("Collect from jar ${entry.name} ${time}ms")
                }
                jarFile.close()
            }
        }

        directories.get().forEach { directory ->
            directory.asFile.walk().forEach action@{ file ->
                if (!inspector.isClass(file)) return@action
                inspector.toIgnoredClass(directory, file)?.let {
                    ignoredClass[ClassData.key(it)] = ClassData(it)
                    return@action
                }
                dispatcher.execute(tasks) {
                    val name = inspector.outputName(directory, file)
                    val time = measureTimeMillis { collect(file.inputStream()) }
                    println("Collect from directory $name ${time}ms")
                }
            }
        }

        tasks.await()
        return CollectResult(ignoredClass, ignoredMethod, mappingMethod)
    }

    private fun collect(inputStream: InputStream) {
        inputStream.use {
            val classReader = ClassReader(it)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            val classVisitor = CollectClassVisitor(classWriter)
            classReader.accept(classVisitor, 0)
        }
    }

    private inner class CollectClassVisitor(
        classVisitor: ClassVisitor,
    ) : ClassVisitor(ASM_API, classVisitor) {
        private var className = ""
        private var superName = ""
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
            this.superName = superName ?: ""
            isModifiable = inspector.isModifiable(access)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor = if (!isModifiable) {
            super.visitMethod(access, name, descriptor, signature, exceptions)
        } else {
            CollectMethodNode(access, name, descriptor, signature, exceptions, className, superName)
        }
    }

    private inner class CollectMethodNode(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
        private val className: String,
        private val superName: String,
    ) : MethodNode(ASM_API, access, name, descriptor, signature, exceptions) {

        override fun visitEnd() {
            super.visitEnd()
            val methodKey = MethodData.key(className, name, desc)
            if (isEmptyMethod() || isGetSetMethod() || isSingleMethod()) {
                ignoredMethod[methodKey] = MethodData(
                    id = INITIAL_ID, access = access,
                    className = className, methodName = name, desc = desc
                )
            } else {
                mappingMethod[methodKey] = MethodData(
                    id = idGenerator.generate(), access = access,
                    className = className, methodName = name, desc = desc
                )
            }
        }

        private fun isEmptyMethod(): Boolean {
            val isConstructor = name == "<init>"
            val instructions = requireNotNull(instructions)
            when {
                !isConstructor -> instructions.forEach {
                    if (it.opcode != -1) return false
                }
                superName != "java/lang/Object" -> return false
                else -> instructions.forEach {
                    when (it.opcode) {
                        -1,
                        Opcodes.ALOAD,
                        Opcodes.INVOKESPECIAL,
                        Opcodes.RETURN,
                        -> return@forEach
                        else -> return false
                    }
                }
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
            instructions.forEach {
                if (it.opcode in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC) return false
            }
            return true
        }
    }
}

internal data class CollectResult(
    val ignoredClass: Map<String, ClassData>,
    val ignoredMethod: Map<String, MethodData>,
    val mappingMethod: Map<String, MethodData>,
) {

    fun isIgnoredClass(className: String): Boolean {
        return ignoredClass.containsKey(ClassData.key(className))
    }
}