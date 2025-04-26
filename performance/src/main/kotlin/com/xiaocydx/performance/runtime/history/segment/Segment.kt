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

package com.xiaocydx.performance.runtime.history.segment

import com.xiaocydx.performance.runtime.history.sample.Sample
import com.xiaocydx.performance.runtime.looper.Scene

/**
 * @author xcc
 * @date 2025/4/25
 */
internal class Segment {
    var isSingle = false
    var scene = Scene.Message
    var startMark = 0L
    var startUptimeMillis = 0L
    var startThreadTimeMillis = 0L
    var endMark = 0L
    var endUptimeMillis = 0L
    var sample: Sample? = null

    //region Metadata
    // scene = Scene.Message
    var log = ""
    var what = 0
    var targetName = ""
    var callbackName = ""
    var arg1 = 0
    var arg2 = 0

    //scene = IdleHandler
    var idleHandlerName = ""

    //scene = NativeTouch
    var action = 0
    var x = 0f
    var y = 0f
    //endregion

    fun reset() {
        copyFrom(emptySegment)
    }

    fun copyFrom(segment: Segment) {
        isSingle = segment.isSingle
        scene = segment.scene
        startMark = segment.startMark
        startUptimeMillis = segment.startUptimeMillis
        startThreadTimeMillis = segment.startThreadTimeMillis
        endMark = segment.endMark
        endUptimeMillis = segment.endUptimeMillis
        sample = segment.sample

        what = segment.what
        targetName = segment.targetName
        callbackName = segment.callbackName
        arg1 = segment.arg1
        arg2 = segment.arg2
        idleHandlerName = segment.idleHandlerName
        action = segment.action
        x = segment.x
        y = segment.y
    }

    private companion object {
        val emptySegment = Segment()
    }
}