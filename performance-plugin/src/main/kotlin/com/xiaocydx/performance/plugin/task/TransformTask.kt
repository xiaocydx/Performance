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
import com.xiaocydx.performance.plugin.metadata.IdGenerator
import com.xiaocydx.performance.plugin.metadata.Inspector
import com.xiaocydx.performance.plugin.output.CacheOutput
import com.xiaocydx.performance.plugin.output.JarOutput
import com.xiaocydx.performance.plugin.processor.CollectProcessor
import com.xiaocydx.performance.plugin.processor.CollectResult
import com.xiaocydx.performance.plugin.processor.ModifyProcessor
import com.xiaocydx.performance.plugin.processor.OutputProcessor
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
import java.io.File
import java.io.File.separator
import java.util.concurrent.Executors
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

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputDirectory
    abstract val cacheDirectory: DirectoryProperty

    private val logger = Logger(javaClass)

    @TaskAction
    fun taskAction() {
        val ext = PerformanceExtension.getHistory(project)
        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        try {
            // Step1: ReadManifest
            val inspector: Inspector
            val output: OutputProcessor
            val idGenerator: IdGenerator
            var time = measureTime {
                val dir = "${project.rootDir}${separator}outputs${separator}"
                inspector = Inspector.create(File(ext.excludeManifest))
                output = OutputProcessor(
                    inspector = inspector,
                    jarOutput = JarOutput(outputJar),
                    cacheOutput = if (ext.isIncrementalEnabled) CacheOutput(cacheDirectory) else null,
                    excludeClassFile = ext.excludeClassFile.ifEmpty { "${dir}ExcludeClassList.text" },
                    excludeMethodFile = ext.excludeMethodFile.ifEmpty { "${dir}ExcludeMethodList.text" },
                    mappingMethodFile = ext.mappingMethodFile.ifEmpty { "${dir}MappingMethodList.text" },
                    mappingBaseFile = ext.mappingBaseFile,
                    executor = executor
                )
                output.scanningCache()
                idGenerator = output.readMappingBase()
            }
            logger.lifecycle { "ReadManifest $time" }

            // Step2: CollectMethod
            val collect: CollectResult
            time = measureTime {
                val processor = CollectProcessor(inspector, idGenerator, output, executor)
                collect = processor.await(inputJars, inputDirectories)
                output.cleanInvalidCache(collect)
                output.writeMappingMethod(collect)
            }
            logger.lifecycle { "CollectMethod $time" }

            // Step3: ModifyMethod
            time = measureTime {
                val processor = ModifyProcessor(collect, output, executor)
                processor.await(ext.isTraceEnabled, ext.isRecordEnabled)
            }
            logger.lifecycle { "ModifyMethod $time" }

            // Step4: AwaitOutput
            time = measureTime { output.await() }
            logger.lifecycle { "AwaitOutput $time" }
        } finally {
            executor.shutdownNow()
        }
    }
}