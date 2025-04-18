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
import com.xiaocydx.performance.plugin.dispatcher.ExecutorDispatcher
import com.xiaocydx.performance.plugin.dispatcher.TaskCountDownLatch
import com.xiaocydx.performance.plugin.dispatcher.execute
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType.ADDED
import org.gradle.work.ChangeType.MODIFIED
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import kotlin.time.measureTime

/**
 * @author xcc
 * @date 2025/4/17
 */
internal abstract class AppendTask : DefaultTask() {

    @get:Incremental
    @get:InputDirectory
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    private val logger = Logger(javaClass)

    @TaskAction
    fun taskAction(inputChanges: InputChanges) {
        val inputDir = input.get().asFile
        val outputDir = output.get().asFile

        val dispatcher = ExecutorDispatcher(16)
        val tasks = TaskCountDownLatch()
        inputChanges
            .getFileChanges(input)
            .asSequence()
            .filter { it.fileType == FileType.FILE }
            .forEach { inputChange ->
                dispatcher.execute(tasks) {
                    val outputPath: String
                    val time = measureTime {
                        outputPath = inputDir.toURI()
                            .relativize(inputChange.file.toURI())
                            .path.substringBeforeLast("_")
                        when(inputChange.changeType) {
                            ADDED -> inputChange.file.copyTo(File(outputDir, outputPath))
                            MODIFIED -> inputChange.file.copyTo(File(outputDir, outputPath), true)
                            REMOVED -> File(outputDir, outputPath).delete()
                            else -> return@measureTime
                        }
                    }
                    logger.debug { "${inputChange.changeType} $outputPath $time" }
                }
            }
        tasks.await()
        dispatcher.shutdownNow()
    }
}