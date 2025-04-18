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

package com.xiaocydx.performance.plugin.task

import com.xiaocydx.performance.plugin.Logger
import com.xiaocydx.performance.plugin.PerformanceExtension
import com.xiaocydx.performance.plugin.dispatcher.ExecutorDispatcher
import com.xiaocydx.performance.plugin.dispatcher.SerialDispatcher
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.processor.CollectProcessor
import com.xiaocydx.performance.plugin.processor.CollectResult
import com.xiaocydx.performance.plugin.processor.MappingProcessor
import com.xiaocydx.performance.plugin.processor.ModifyProcessor
import com.xiaocydx.performance.plugin.processor.OutputProcessor
import com.xiaocydx.performance.plugin.processor.await
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.concurrent.Future
import kotlin.time.measureTime

/**
 * @author xcc
 * @date 2025/4/11
 */
internal abstract class TransformTask : DefaultTask() {

    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    @get:OutputDirectory
    abstract val outputExclude: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    private val logger = Logger(javaClass)

    @TaskAction
    fun taskAction() {
        val ext = PerformanceExtension.getHistory(project)
        val rootDir = project.rootDir.absolutePath
        val consumer = SerialDispatcher.single()
        val producer = ExecutorDispatcher(threads = Runtime.getRuntime().availableProcessors() - 1)
        try {
            // Step1: ReadManifest
            val inspector: Inspector
            val idGenerator: IdGenerator
            val mappingProcessor: MappingProcessor
            var time = measureTime {
                mappingProcessor = MappingProcessor(
                    dispatcher = producer,
                    excludeManifest = ext.excludeManifest,
                    excludeClassFile = ext.excludeClassFile.ifEmpty { "${rootDir}/outputs/ExcludeClassList.text" },
                    excludeMethodFile = ext.excludeMethodFile.ifEmpty { "${rootDir}/outputs/ExcludeMethodList.text" },
                    mappingMethodFile = ext.mappingMethodFile.ifEmpty { "${rootDir}/outputs/MappingMethodList.text" }
                )
                val result = mappingProcessor.read()
                inspector = result.inspector
                idGenerator = result.idGenerator
            }
            logger.lifecycle { "ReadManifest $time" }

            // Step2: CollectMethod
            val collectResult: CollectResult
            val outputProcessor: OutputProcessor
            time = measureTime {
                outputProcessor = OutputProcessor(consumer, inspector, outputExclude, outputJar)
                val collectProcessor = CollectProcessor(producer, inspector, idGenerator, outputProcessor)
                collectResult = collectProcessor.await(inputJars, inputDirectories)
                collectResult.excludeFiles // TODO: 删除outputExclude不存在于excludeFiles的文件
            }
            logger.lifecycle { "CollectMethod $time" }

            // Step3: ModifyMethod
            val writeMapping: Future<Unit>
            time = measureTime {
                writeMapping = mappingProcessor.submitWrite(collectResult)
                val modifyProcessor = ModifyProcessor(producer, collectResult, outputProcessor)
                modifyProcessor.await(ext.isTraceEnabled, ext.isRecordEnabled)
            }
            logger.lifecycle { "ModifyMethod $time" }

            // Step4: AwaitOutput
            time = measureTime {
                writeMapping.await()
                outputProcessor.await()
            }
            logger.lifecycle { "AwaitOutput $time" }
        } finally {
            consumer.shutdownNow()
            producer.shutdownNow()
        }
    }
}