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
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.ScopedArtifacts
import com.xiaocydx.performance.plugin.task.AppendTask
import com.xiaocydx.performance.plugin.task.GenerateTask
import com.xiaocydx.performance.plugin.task.TransformTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

internal class PerformancePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("Performance Plugin, Android Application plugin required.")
        }
        PerformanceExtension.inject(project)
        project.tasks.register("performanceGenerateJson", GenerateTask::class.java)

        val androidExt = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidExt.onVariants { variant ->
            val historyExt = PerformanceExtension.getHistory(project)
            if (!historyExt.isTraceEnabled && !historyExt.isRecordEnabled) return@onVariants

            val transformTaskProvider = project.tasks.register(
                "performanceTransform${variant.taskSuffix()}",
                TransformTask::class.java
            )
            transformTaskProvider.configure {
                val buildDir = PerformanceExtension.buildDir(project)
                it.cacheDirectory.set(File(buildDir, variant.name))
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

            if (historyExt.isIncremental) {
                val appendTaskProvider = project.tasks.register(
                    "performanceAppend${variant.taskSuffix()}",
                    AppendTask::class.java
                )
                appendTaskProvider.configure {
                    it.input.set(transformTaskProvider.get().cacheDirectory)
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

    private fun ComponentIdentity.taskSuffix(): String {
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}