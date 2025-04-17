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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType.*
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

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

    @TaskAction
    fun taskAction(inputChanges: InputChanges) {
        val inputDir = input.get().asFile
        val outputDir = output.get().asFile
        inputChanges
            .getFileChanges(input)
            .asSequence()
            .filter { it.fileType == FileType.FILE }
            .forEach { inputChange ->
                val outputPath = inputDir.toURI()
                    .relativize(inputChange.file.toURI()).path
                    .substringBeforeLast("_")
                when(inputChange.changeType) {
                    ADDED -> inputChange.file.copyTo(File(outputDir, outputPath))
                    MODIFIED -> inputChange.file.copyTo(File(outputDir, outputPath), true)
                    REMOVED -> File(outputDir, outputPath).delete()
                    else -> return@forEach
                }
            }
    }
}