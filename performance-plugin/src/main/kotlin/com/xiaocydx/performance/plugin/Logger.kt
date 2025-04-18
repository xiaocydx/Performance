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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.logging.Logger as GradleLogger

/**
 * @author xcc
 * @date 2025/4/18
 */
internal class Logger(val value: GradleLogger) {

    constructor(name: String) : this(Logging.getLogger(name))

    constructor(clazz: Class<*>) : this(Logging.getLogger(clazz))

    inline fun debug(message: () -> String) {
        log(LogLevel.DEBUG, message)
    }

    inline fun info(message: () -> String) {
        log(LogLevel.INFO, message)
    }

    inline fun lifecycle(message: () -> String) {
        log(LogLevel.LIFECYCLE, message)
    }

    inline fun warn(message: () -> String) {
        log(LogLevel.WARN, message)
    }

    inline fun quiet(message: () -> String) {
        log(LogLevel.QUIET, message)
    }

    inline fun error(message: () -> String) {
        log(LogLevel.ERROR, message)
    }

    inline fun log(level: LogLevel, message: () -> String) {
        value.takeIf { it.isEnabled(level) }?.log(level, message())
    }
}