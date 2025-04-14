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

@file:Suppress("CanBeParameter")

package com.xiaocydx.performance.plugin.enforcer

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * @author xcc
 * @date 2025/4/13
 */
internal class OutputEnforcer(
    private val dispatcher: Dispatcher,
    private val output: RegularFileProperty,
) : AbstractEnforcer() {
    private val jarOutput = JarOutputStream(output.get().asFile.outputStream().buffered())
    private val taskCount = TaskCountDownLatch()

    fun write(name: String, bytes: ByteArray) {
        taskCount.increment()
        dispatcher.execute {
            jarOutput.putNextEntry(JarEntry(name))
            jarOutput.write(bytes)
            jarOutput.closeEntry()
            taskCount.decrement()
        }
    }

    fun write(inputJars: ListProperty<RegularFile>) {
        taskCount.increment()
        dispatcher.execute {
            inputJars.get().forEach { file ->
                println("handling " + file.asFile.absolutePath)
                val jarFile = JarFile(file.asFile)
                jarFile.entries().iterator().forEach { jarEntry ->
                    println("Adding from jar ${jarEntry.name}")
                    jarOutput.putNextEntry(JarEntry(jarEntry.name))
                    jarFile.getInputStream(jarEntry).use {
                        it.copyTo(jarOutput)
                    }
                    jarOutput.closeEntry()
                }
                jarFile.close()
            }
            taskCount.decrement()
        }
    }

    fun await() {
        taskCount.await()
        jarOutput.close()
    }
}