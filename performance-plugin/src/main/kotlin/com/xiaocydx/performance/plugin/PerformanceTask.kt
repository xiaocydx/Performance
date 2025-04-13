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

import com.xiaocydx.performance.plugin.dispatcher.NopDispatcher
import com.xiaocydx.performance.plugin.handler.ModifyHandler
import com.xiaocydx.performance.plugin.handler.OutputHandler
import com.xiaocydx.performance.plugin.mapping.MappingMethod
import com.xiaocydx.performance.plugin.mapping.MappingReader
import com.xiaocydx.performance.plugin.mapping.MappingWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import kotlin.system.measureTimeMillis

/**
 * @author xcc
 * @date 2025/4/11
 */
internal abstract class PerformanceTask : DefaultTask() {

    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun taskAction() {
        val time = measureTimeMillis {
            val consumerDispatcher = NopDispatcher
            val producerDispatcher = NopDispatcher

            val outputHandler = OutputHandler(consumerDispatcher, output)
            val modifyHandler = ModifyHandler(producerDispatcher, outputHandler)

            modifyHandler.submitJars(inputJars)
            modifyHandler.submitDirectories(inputDirectories)

            modifyHandler.awaitComplete()
            outputHandler.awaitComplete()

            consumerDispatcher.shutdownNow()
            producerDispatcher.shutdownNow()
        }
        println("PerformanceTask -> time = $time ms")
    }

    private fun read() {
        // TODO: 实现增量才需要Reader
        val reader = MappingReader(handledMethodFile())
        val previousHandled = reader.read()
        val idGenerator = reader.idGenerator(previousHandled)
        previousHandled
    }

    private fun writer() {
        val currentIgnored = (30..50).map {
            MappingMethod(id = it, accessFlag = 0, className = "class-$it", methodName = "method-$it")
        }
        val currentHandled = (1..20).map {
            MappingMethod(id = it, accessFlag = 0, className = "class-$it", methodName = "method-$it")
        }

        val writer = MappingWriter(
            ignoredMethodFile = ignoredMethodFile(),
            handledMethodFile = handledMethodFile()
        )
        writer.writeIgnored(currentIgnored)
        writer.writeHandled(currentHandled)
    }

    private fun ignoredMethodFile() = kotlin.run {
        "${project.rootDir}/outputs/ignoredMethodMapping.text"
    }

    private fun handledMethodFile() = kotlin.run {
        "${project.rootDir}/outputs/handledMethodMapping.text"
    }
}