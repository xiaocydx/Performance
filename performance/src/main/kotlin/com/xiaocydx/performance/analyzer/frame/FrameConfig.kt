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

package com.xiaocydx.performance.analyzer.frame

/**
 * @author xcc
 * @date 2025/4/3
 */
data class FrameConfig(val receivers: List<FrameMetricsReceiver> = emptyList()) {

    internal fun checkProperty() {
        receivers.forEach { receiver ->
            val name = receiver.javaClass.simpleName
            val threshold = receiver.dropLevelThreshold
            require(receiver.intervalMillis >= 0) {
                "${name}.intervalMillis < 0"
            }
            require(threshold.best >= 0) {
                "${name}.dropLevelThreshold.best < 0"
            }
            require(threshold.normal > threshold.best) {
                "${name}.dropLevelThreshold.normal <= ${name}.dropLevelThreshold.best"
            }
            require(threshold.middle > threshold.normal) {
                "${name}.dropLevelThreshold.middle <= ${name}.dropLevelThreshold.normal"
            }
            require(threshold.high > threshold.middle) {
                "${name}.dropLevelThreshold.high <= ${name}.dropLevelThreshold.middle"
            }
            require(threshold.frozen > threshold.high) {
                "${name}.dropLevelThreshold.frozen <= ${name}.dropLevelThreshold.high"
            }
        }
    }
}