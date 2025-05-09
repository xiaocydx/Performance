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

package com.xiaocydx.performance.plugin.metadata

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File

/**
 * @author xcc
 * @date 2025/4/15
 */
internal class ClassData(
    val className: String,
    val entryName: String = "",
    val cacheFile: File? = null,
    private val classReader: ClassReader? = null,
    private val classNode: ClassNode? = null
) : Metadata {
    override val key = className

    fun requireReader() = requireNotNull(classReader)

    fun requireNode() = requireNotNull(classNode)

    override fun toOutput() = className
}