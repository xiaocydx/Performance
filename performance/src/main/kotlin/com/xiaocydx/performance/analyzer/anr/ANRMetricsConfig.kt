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

package com.xiaocydx.performance.analyzer.anr

/**
 * @author xcc
 * @date 2025/4/24
 */
data class ANRMetricsConfig(val receiver: ANRMetricsReceiver) {

    internal fun checkProperty() {
        val name = receiver.javaClass.name
        require(receiver.idleThresholdMillis >= 0) {
            "${name}.idleThresholdMillis < 0"
        }
        require(receiver.mergeThresholdMillis >= 0) {
            "${name}.mergeThresholdMillis < 0"
        }
    }
}