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
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

internal class PerformancePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("Performance Plugin, Android Application plugin required.")
        }
        PerformanceExtension.inject(project)

        val androidExt = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidExt.onVariants { variant ->
            val historyExt = PerformanceExtension.getHistory(project)
            if (!historyExt.isEnabled) return@onVariants

            val taskProvider = project.tasks.register(
                "${variant.name}PerformanceHistory",
                PerformanceHistoryTask::class.java
            )
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(taskProvider)
                .toTransform(
                    type = ScopedArtifact.CLASSES,
                    inputJars = PerformanceHistoryTask::inputJars,
                    inputDirectories = PerformanceHistoryTask::inputDirectories,
                    into = PerformanceHistoryTask::output
                )
        }
    }
}