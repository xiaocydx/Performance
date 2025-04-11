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

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters.None
import groovyjarjarasm.asm.Opcodes.ASM9
import org.objectweb.asm.ClassVisitor

/**
 * @author xcc
 * @date 2025/4/11
 */
internal abstract class PerformanceClassVisitorFactory : AsmClassVisitorFactory<None> {

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = PerformanceClassVisitor(
        api = ASM9,
        nextClassVisitor = nextClassVisitor,
        className = classContext.currentClassData.className
    )

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.endsWith("PerformanceTest")
    }
}