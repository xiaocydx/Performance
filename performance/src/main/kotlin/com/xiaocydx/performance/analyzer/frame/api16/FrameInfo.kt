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

package com.xiaocydx.performance.analyzer.frame.api16

/**
 * @author xcc
 * @date 2025/4/7
 */
internal class FrameInfo {
    var frameStartNanos = 0L; private set
    var inputStartNanos = 0L; private set
    var animationStartNanos = 0L; private set
    var traversalsStartNanos = 0L; private set
    var preDrawStartNanos = 0L; private set
    var drawStartNanos = 0L; private set
    var frameEndNanos = 0L; private set

    fun markPreDrawStart() {
        preDrawStartNanos = System.nanoTime()
    }

    fun markDrawStart() {
        drawStartNanos = System.nanoTime()
    }

    fun sync(frameInfo: ChoreographerFrameInfo) {
        frameStartNanos = frameInfo.frameStartNanos
        inputStartNanos = frameInfo.inputStartNanos
        animationStartNanos = frameInfo.animationStartNanos
        traversalsStartNanos = frameInfo.traversalsStartNanos
        frameEndNanos = frameInfo.frameEndNanos
    }
}