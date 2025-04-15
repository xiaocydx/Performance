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

import com.xiaocydx.performance.plugin.dispatcher.Dispatcher
import com.xiaocydx.performance.plugin.dispatcher.ExecutorDispatcher
import com.xiaocydx.performance.plugin.dispatcher.SerialDispatcher
import com.xiaocydx.performance.plugin.enforcer.CollectEnforcer
import com.xiaocydx.performance.plugin.enforcer.MappingEnforcer
import com.xiaocydx.performance.plugin.enforcer.ModifyEnforcer
import com.xiaocydx.performance.plugin.enforcer.OutputEnforcer
import com.xiaocydx.performance.plugin.enforcer.await
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * @author xcc
 * @date 2025/4/11
 */
internal abstract class PerformanceHistoryTask : DefaultTask() {

    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun taskAction() {
        val historyExt = PerformanceExtension.getHistory(project)
        val isNop = true
        val producerDispatcher: Dispatcher
        val consumerDispatcher: SerialDispatcher
        if (isNop) {
            producerDispatcher = SerialDispatcher.nop()
            consumerDispatcher = SerialDispatcher.nop()
        } else {
            producerDispatcher = ExecutorDispatcher(threads = 16)
            consumerDispatcher = SerialDispatcher.single()
        }

        // Step1: ReadMapping
        var startTime = System.currentTimeMillis()
        val mappingEnforcer = MappingEnforcer(
            dispatcher = producerDispatcher,
            keepMethodFile = historyExt.keepMethodFile,
            ignoredClassFile = historyExt.ignoredClassFile.ifEmpty {
                "${project.rootDir}/outputs/ignoredClassList.text"
            },
            ignoredMethodFile = historyExt.ignoredMethodFile.ifEmpty {
                "${project.rootDir}/outputs/ignoredMethodList.text"
            },
            mappingMethodFile = historyExt.mappingMethodFile.ifEmpty {
                "${project.rootDir}/outputs/mappingMethodList.text"
            }
        )
        val (inspector, idGenerator, _) = mappingEnforcer.read()
        printTime(startTime, step = "ReadMapping")

        // Step2: CollectMethod
        startTime = System.currentTimeMillis()
        val collectEnforcer = CollectEnforcer(producerDispatcher, idGenerator, inspector)
        val collectResult = collectEnforcer.await(inputJars, inputDirectories)
        printTime(startTime, step = "CollectMethod")

        // Step3: ModifyMethod
        startTime = System.currentTimeMillis()
        val writeMapping = mappingEnforcer.submitWrite(collectResult)
        val outputEnforcer = OutputEnforcer(consumerDispatcher, output, inspector)
        val modifyEnforcer = ModifyEnforcer(producerDispatcher, outputEnforcer, inspector)
        modifyEnforcer.await(inputJars, inputDirectories, collectResult)
        outputEnforcer.await()
        writeMapping.await()
        printTime(startTime, step = "ModifyMethod")

        consumerDispatcher.shutdownNow()
        producerDispatcher.shutdownNow()
    }

    private fun printTime(startTime: Long, step: String) {
        println("PerformanceTask -> $step time = ${System.currentTimeMillis() - startTime} ms")
    }
}