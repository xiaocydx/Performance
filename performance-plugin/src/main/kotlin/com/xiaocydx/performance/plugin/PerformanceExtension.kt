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

/**
 * @author xcc
 * @date 2025/4/14
 */
open class PerformanceExtension {

    internal companion object {
        private const val PERFORMANCE_NAME = "performance"
        private const val HISTORY_NAME = "history"

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

open class PerformanceHistoryExtension(
    var isTraceEnabled: Boolean = false,
    var isRecordEnabled: Boolean = false,
    var excludeManifest: String = "",
    var excludeClassFile: String = "",
    var excludeMethodFile: String = "",
    var mappingMethodFile: String = "",
    var mappingSnapshotDir: String = ""
)