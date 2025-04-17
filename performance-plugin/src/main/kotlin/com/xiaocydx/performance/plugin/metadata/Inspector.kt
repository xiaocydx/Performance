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

import org.gradle.api.file.Directory
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.util.jar.JarEntry

/**
 * @author xcc
 * @date 2025/4/15
 */
internal class Inspector private constructor(
    private val keepClass: Set<String>,
    private val keepPackage: Set<String>,
) {

    fun isClass(entry: JarEntry): Boolean {
        if (entry.isDirectory) return false
        return entry.name.endsWith(".class")
    }

    fun isClass(file: File): Boolean {
        if (!file.isFile) return false
        return file.name.endsWith(".class")
    }

    fun toIgnoredClass(entry: JarEntry): String? {
        val entryName = entry.name
        val className = className(entry)
        DEFAULT_FILTER_CLASS.forEach { if (entryName.contains(it)) return className }
        if (isKeepClass(className)) return className
        return null
    }

    fun toIgnoredClass(directory: Directory, file: File): String? {
        val fileName = file.name
        val className = className(directory, file)
        DEFAULT_FILTER_CLASS.forEach { if (fileName.contains(it)) return className }
        if (isKeepClass(className)) return className
        return null
    }

    fun isModifiable(node: ClassNode): Boolean {
        return node.access and Opcodes.ACC_INTERFACE == 0
    }

    fun isModifiable(node: MethodNode): Boolean {
        if (node.access and Opcodes.ACC_ABSTRACT != 0
                || node.access and Opcodes.ACC_NATIVE != 0) {
            return false
        }
        return !node.isEmpty() && !node.isGetSet() && !node.isNotContainsInvoke()
    }

    fun isWritable(file: File): Boolean {
        return isClass(file)
    }

    fun isWritable(entry: JarEntry): Boolean {
        return isClass(entry) && !entry.name.contains(MODULE_INFO_CLASS)
    }

    fun className(entry: JarEntry): String {
        require(entry.name.endsWith(".class"))
        return entry.name.replace(".class", "")
    }

    fun className(directory: Directory, file: File): String {
        require(file.name.endsWith(".class"))
        val path = directory.asFile.toURI().relativize(file.toURI()).path
        val pathName = path.replace(File.separatorChar, '/')
        return pathName.replace(".class", "")
    }

    fun entryName(directory: Directory, file: File): String {
        val path = directory.asFile.toURI().relativize(file.toURI()).path
        return path.replace(File.separatorChar, '/')
    }

    private fun isKeepClass(className: String): Boolean {
        if (keepClass.contains(className)) return true
        val last = className.lastIndexOf('/')
        val end = (last + 1).coerceAtMost(className.length)
        val packageName = className.substring(0, end)
        keepPackage.forEach { if (packageName.startsWith(it)) return true }
        return false
    }

    private fun MethodNode.isEmpty(): Boolean {
        val isConstructor = name == "<init>"
        val instructions = requireNotNull(instructions)
        if (!isConstructor) {
            instructions.forEach { if (it.opcode != -1) return false }
        } else {
            var loadCount = 0
            var invokeCount = 0
            var returnCount = 0
            instructions.forEach {
                when (it.opcode) {
                    -1 -> return@forEach
                    Opcodes.ALOAD -> loadCount++
                    Opcodes.INVOKESPECIAL -> invokeCount++
                    Opcodes.RETURN -> returnCount++
                    else -> return false
                }
            }
            // 空构造函数只执行这三种指令
            if (loadCount != 1 || invokeCount != 1 || returnCount != 1) return false
        }
        return true
    }

    private fun MethodNode.isGetSet(): Boolean {
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

    private fun MethodNode.isNotContainsInvoke(): Boolean {
        val instructions = requireNotNull(instructions)
        instructions.forEach {
            if (it.opcode in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC) return false
        }
        return true
    }

    companion object {
        private const val MODULE_INFO_CLASS = "module-info.class"
        private val DEFAULT_FILTER_CLASS = listOf(MODULE_INFO_CLASS, "R.class", "R$", "Manifest", "BuildConfig")

        private const val KEEP_CLASS_PREFIX = "-keepclass "
        private const val KEEP_PACKAGE_PREFIX = "-keeppackage "
        private val DEFAULT_KEEP_PACKAGE = listOf("android/", "com/xiaocydx/performance/")

        fun create(keepMethodFile: File): Inspector {
            val keepClass = mutableSetOf<String>()
            val keepPackage = mutableSetOf<String>()
            keepPackage.addAll(DEFAULT_KEEP_PACKAGE)
            if (keepMethodFile.exists()) {
                keepMethodFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        when {
                            line.startsWith(KEEP_CLASS_PREFIX) -> {
                                keepClass.add(line.replace(KEEP_CLASS_PREFIX, ""))
                            }
                            line.startsWith(KEEP_PACKAGE_PREFIX) -> {
                                keepPackage.add(line.replace(KEEP_PACKAGE_PREFIX, ""))
                            }
                        }
                    }
                }
            }
            return Inspector(keepClass, keepPackage)
        }
    }
}