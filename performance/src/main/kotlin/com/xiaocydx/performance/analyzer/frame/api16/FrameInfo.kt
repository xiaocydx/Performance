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
    private var preDrawStartNanos = 0L
    private var isFirstCompleted = false

    var inputNanos = 0L; private set
    var animationNanos = 0L; private set
    var layoutMeasureNanos = 0L; private set
    var drawNanos = 0L; private set
    var totalNanos = 0L; private set
    var isFirstDrawFrame = false; private set

    fun markPreDrawStart() {
        preDrawStartNanos = System.nanoTime()
    }

    fun merge(frameInfo: ChoreographerFrameInfo): Boolean = with(frameInfo) {
        if (preDrawStartNanos < frameStartNanos) return@with false
        inputNanos = (animationStartNanos - inputStartNanos).coerceAtLeast(0L)
        animationNanos = (traversalStartNanos - animationStartNanos).coerceAtLeast(0L)
        layoutMeasureNanos = (preDrawStartNanos - traversalStartNanos).coerceAtLeast(0L)
        drawNanos = (frameEndNanos - preDrawStartNanos).coerceAtLeast(0L)
        totalNanos = (frameEndNanos - frameStartNanos).coerceAtLeast(0L)
        isFirstDrawFrame = !isFirstCompleted
        isFirstCompleted = true
        true
    }
}