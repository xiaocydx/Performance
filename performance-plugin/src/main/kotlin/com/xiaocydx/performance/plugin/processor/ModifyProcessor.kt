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

package com.xiaocydx.performance.plugin.processor

import com.xiaocydx.performance.plugin.metadata.MethodData
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter.INVOKESTATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.concurrent.ExecutorService
import kotlin.time.measureTime

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class ModifyProcessor(
    private val result: CollectResult,
    private val output: OutputProcessor,
    private val executor: ExecutorService
) : AbstractProcessor() {

    fun await(isTraceEnabled: Boolean, isRecordEnabled: Boolean) {
        val tasks = TaskCountDownLatch()
        result.mappingClass.forEach {
            executor.execute(tasks) {
                val cache = it.value.cacheFile
                if (cache?.exists() == true) return@execute
                val entryName = it.value.entryName
                val time = measureTime {
                    val classReader = it.value.requireReader()
                    val classNode = it.value.requireNode()
                    val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    modify(isTraceEnabled, isRecordEnabled, classNode)
                    classNode.accept(classWriter)
                    val bytes = classWriter.toByteArray()
                    output.write(entryName, bytes, cache)
                }
                logger.debug { "Modify $entryName $time" }
            }
        }
        tasks.await()
    }

    private fun modify(isTraceEnabled: Boolean, isRecordEnabled: Boolean, classNode: ClassNode) {
        var traceClassName = ""
        if (isTraceEnabled) {
            val hash = classNode.name.hashCode() and CLASS_NAME_HASH_CODE_MASK
            traceClassName = "(${hash}) ${classNode.name.substringAfterLast("/")}"
        }

        fun methodEnterInstructions(methodData: MethodData) = InsnList().apply {
            if (isTraceEnabled) {
                add(LdcInsnNode("${traceClassName}.${methodData.methodName}"))
                add(MethodInsnNode(INVOKESTATIC, HISTORY, "beginTrace", "(Ljava/lang/String;)V", false))
            }
            if (isRecordEnabled) {
                add(LdcInsnNode(methodData.id))
                add(MethodInsnNode(INVOKESTATIC, HISTORY, "enter", "(I)V", false))
            }
        }

        fun methodExitInstruction(methodData: MethodData) = InsnList().apply {
            if (isRecordEnabled) {
                add(LdcInsnNode(methodData.id))
                add(MethodInsnNode(INVOKESTATIC, HISTORY, "exit", "(I)V", false))
            }
            if (isTraceEnabled) {
                add(MethodInsnNode(INVOKESTATIC, HISTORY, "endTrace", "()V", false))
            }
        }

        classNode.methods.forEach { method ->
            val methodKey = MethodData.key(classNode.name, method.name, method.desc)
            val methodData = result.mappingMethod[methodKey] ?: return@forEach
            method.instructions.insert(methodEnterInstructions(methodData))
            method.instructions.forEach { insnNode ->
                val opcode = insnNode.opcode
                if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                    // method.instructions是双向链表，遍历过程对insnNode插入prev节点，不影响遍历
                    method.instructions.insertBefore(insnNode, methodExitInstruction(methodData))
                }
            }
        }
    }

    private companion object {
        const val HISTORY = "com/xiaocydx/performance/runtime/history/History"
        const val CLASS_NAME_HASH_CODE_MASK = 0xFFFF
    }
}