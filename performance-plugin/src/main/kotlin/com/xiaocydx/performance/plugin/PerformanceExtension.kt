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

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import java.io.File
import java.io.File.separator
import java.io.PrintWriter

/**
 * @author xcc
 * @date 2025/4/14
 */
open class PerformanceExtension {

    internal companion object {
        private const val PERFORMANCE_NAME = "performance"
        private const val HISTORY_NAME = "history"

        fun buildDir(project: Project): String {
            val projectBuildDir = project.layout.buildDirectory.asFile.get()
            return "${projectBuildDir.absolutePath}${separator}$PERFORMANCE_NAME"
        }

        fun inject(project: Project) {
            val performance = project.extensions.create(PERFORMANCE_NAME, PerformanceExtension::class.java)
            (performance as ExtensionAware).extensions.create(HISTORY_NAME, PerformanceHistoryExtension::class.java)
        }

        fun getHistory(project: Project): PerformanceHistoryExtension {
            val performance = project.extensions.getByType(PerformanceExtension::class.java)
            return (performance as ExtensionAware).extensions.getByType(PerformanceHistoryExtension::class.java)
        }
    }
}

/**
 * `@JvmField support Groovy`
 */
open class PerformanceHistoryExtension(
    @JvmField var isTraceEnabled: Boolean = false,
    @JvmField var isRecordEnabled: Boolean = false,
    @JvmField var isIncrementalEnabled: Boolean = true,
    @JvmField var excludeManifest: String = "",
    @JvmField var excludeClassFile: String = "",
    @JvmField var excludeMethodFile: String = "",
    @JvmField var mappingMethodFile: String = "",
    @JvmField var mappingBaseFile: String = "",
    @JvmField var snapshotDir: String = ""
) {

    fun buildManifest(path: String, block: ExcludeManifest.() -> Unit): String {
        val file = File(path)
        file.parentFile.takeIf { !it.exists() }?.mkdirs()
        file.printWriter().use { ExcludeManifest(it).apply(block) }
        return path
    }

    internal fun setDefaultProperty(project: Project) = apply {
        val buildDir = PerformanceExtension.buildDir(project)
        excludeClassFile = excludeClassFile.ifEmpty {
            "${buildDir}${separator}exclude${separator}ExcludeClassList.text"
        }
        excludeMethodFile = excludeMethodFile.ifEmpty {
            "${buildDir}${separator}exclude${separator}ExcludeMethodList.text"
        }
        mappingMethodFile = mappingMethodFile.ifEmpty {
            "${buildDir}${separator}mapping${separator}MappingMethodList.text"
        }
        if (snapshotDir.isNotEmpty()) File(snapshotDir).takeIf { !it.exists() }?.mkdirs()
    }

    class ExcludeManifest(private val writer: PrintWriter) {

        fun addPackage(vararg value: String) = apply {
            value.forEach { writer.println("-package $it") }
        }

        fun addClass(vararg value: String) = apply {
            value.forEach { writer.println("-class $it") }
        }
    }
}