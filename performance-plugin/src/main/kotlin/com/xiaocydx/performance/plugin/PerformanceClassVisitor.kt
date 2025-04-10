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

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

/**
 * @author xcc
 * @date 2025/4/10
 */
internal class PerformanceClassVisitor(
    api: Int,
    nextClassVisitor: ClassVisitor,
    private val className: String,
) : ClassVisitor(api, nextClassVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return PerformanceMethodVisitor(api, methodVisitor, access, name, descriptor, className)
    }
}