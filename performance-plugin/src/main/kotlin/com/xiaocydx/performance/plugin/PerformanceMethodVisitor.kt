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

package com.xiaocydx.performance.plugin

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.commons.AdviceAdapter

/**
 * @author xcc
 * @date 2025/4/10
 */
internal class PerformanceMethodVisitor(
    api: Int,
    methodVisitor: MethodVisitor?,
    access: Int,
    name: String?,
    descriptor: String?,
) : AdviceAdapter(api, methodVisitor, access, name, descriptor) {

    override fun onMethodEnter() {
        // TODO: 实现id的分配
        // TODO: 空函数不处理
        val id = name.hashCode()
        mv.visitLdcInsn(id)
        mv.visitMethodInsn(INVOKESTATIC, HISTORY, ENTER, DESCRIPTOR, false)
    }

    override fun onMethodExit(opcode: Int) {
        // TODO: 实现id的分配
        // TODO: 空函数不处理
        val id = name.hashCode()
        mv.visitLdcInsn(id)
        mv.visitMethodInsn(INVOKESTATIC, HISTORY, EXIT, DESCRIPTOR, false)
    }

    private companion object {
        const val HISTORY = "com/xiaocydx/performance/runtime/history/History"
        const val ENTER = "enter"
        const val EXIT = "exit"
        const val DESCRIPTOR = "(I)V"
    }
}