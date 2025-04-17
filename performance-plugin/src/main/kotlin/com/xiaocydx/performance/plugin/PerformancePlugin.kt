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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.xiaocydx.performance.plugin.task.AppendTask
import com.xiaocydx.performance.plugin.task.TransformTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.io.File.separator

internal class PerformancePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("Performance Plugin, Android Application plugin required.")
        }
        PerformanceExtension.inject(project)

        val projectBuildDir = project.layout.buildDirectory.asFile.get()
        val buildDir = "${projectBuildDir.absolutePath}${separator}performance"
        val androidExt = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidExt.onVariants { variant ->
            val historyExt = PerformanceExtension.getHistory(project)
            if (!historyExt.isTraceEnabled && !historyExt.isRecordEnabled) return@onVariants

            val transformTaskProvider = project.tasks.register(
                "${variant.name}PerformanceTransform",
                TransformTask::class.java
            )
            transformTaskProvider.configure { task ->
                val excludeDir = File(buildDir, "exclude${separator}${variant.name}")
                excludeDir.takeIf { !it.exists() }?.mkdirs()
                task.outputExclude.set(excludeDir)
            }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.ALL)
                .use(transformTaskProvider)
                .toTransform(
                    type = ScopedArtifact.CLASSES,
                    inputJars = TransformTask::inputJars,
                    inputDirectories = TransformTask::inputDirectories,
                    into = TransformTask::outputJar
                )

            val appendTaskProvider = project.tasks.register(
                "${variant.name}PerformanceAppend",
                AppendTask::class.java
            )
            appendTaskProvider.configure {
                it.input.set(transformTaskProvider.get().outputExclude)
            }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.ALL)
                .use(appendTaskProvider)
                .toAppend(
                    to = ScopedArtifact.CLASSES,
                    with = AppendTask::output
                )
        }
    }
}