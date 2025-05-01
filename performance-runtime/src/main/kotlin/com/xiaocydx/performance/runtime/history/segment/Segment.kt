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

import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.Metadata
import com.xiaocydx.performance.runtime.looper.Scene
import com.xiaocydx.performance.runtime.looper.Scene.IdleHandler
import com.xiaocydx.performance.runtime.looper.Scene.Message
import com.xiaocydx.performance.runtime.looper.Scene.NativeInput
import com.xiaocydx.performance.runtime.looper.Start

/**
 * @author xcc
 * @date 2025/4/25
 */
internal data class Segment(
    var isSingle: Boolean = false,
    var needRecord: Boolean = false,
    var needSample: Boolean = false,

    //region collectFrom DispatchContext
    var scene: Scene = Message,
    var startMark: Long = 0L,
    var startUptimeMillis: Long = 0L,
    var startThreadTimeMillis: Long = 0L,
    var endMark: Long = 0L,
    var endUptimeMillis: Long = 0L,
    var endThreadTimeMillis: Long = 0L,

    //region Metadata
    // scene = Message
    var log: String = "",
    var `when`: Long = 0L,
    var what: Int = 0,
    var targetName: String? = null,
    var callbackName: String? = null,
    var arg1: Int = 0,
    var arg2: Int = 0,

    // scene = IdleHandler
    var idleHandlerName: String = "",

    // scene = NativeInput
    var isTouch: Boolean = false,
    var action: Int = 0,
    var keyCode: Int = 0,
    var rawX: Float = 0f,
    var rawY: Float = 0f,
    //endregion
    //endregion
) {
    val wallDurationMillis: Long
        get() = endUptimeMillis - startUptimeMillis

    fun reset() {
        copyFrom(emptySegment)
    }

    fun copyFrom(segment: Segment) {
        isSingle = segment.isSingle
        needRecord = segment.needRecord
        needSample = segment.needSample

        scene = segment.scene
        startMark = segment.startMark
        startUptimeMillis = segment.startUptimeMillis
        startThreadTimeMillis = segment.startThreadTimeMillis
        endMark = segment.endMark
        endUptimeMillis = segment.endUptimeMillis
        endThreadTimeMillis = segment.endThreadTimeMillis

        `when` = segment.`when`
        what = segment.what
        targetName = segment.targetName
        callbackName = segment.callbackName
        arg1 = segment.arg1
        arg2 = segment.arg2
        idleHandlerName = segment.idleHandlerName
        action = segment.action
        rawX = segment.rawX
        rawY = segment.rawY
    }

    fun metadata() = when (scene) {
        Message -> log.ifEmpty {
            Metadata.messageToString(
                `when` = `when`,
                what = what,
                targetName = targetName,
                callbackName = callbackName,
                arg1 = arg1,
                arg2 = arg2,
                uptimeMillis = startUptimeMillis
            )
        }
        IdleHandler -> {
            Metadata.idleHandlerToString(idleHandlerName)
        }
        NativeInput -> if (isTouch) {
            Metadata.motionEventToString(action, rawX, rawY)
        } else {
            Metadata.keyEventToString(action, keyCode)
        }
    }

    private companion object {
        val emptySegment = Segment()
    }
}

internal fun Segment.collectFrom(current: DispatchContext) {
    when (current) {
        is Start -> {
            scene = current.scene
            startMark = current.mark
            startUptimeMillis = current.uptimeMillis
            startThreadTimeMillis = current.threadTimeMillis
        }
        is End -> {
            endMark = current.mark
            endUptimeMillis = current.uptimeMillis
            when (current.scene) {
                Message -> {
                    current.metadata.asMessageLog()?.let {
                        log = it
                        return
                    }
                    current.metadata.asMessage()!!.let {
                        `when` = it.`when`
                        what = it.what
                        targetName = it.target?.javaClass?.name
                        callbackName = it.callback?.javaClass?.name
                        arg1 = it.arg1
                        arg2 = it.arg2
                    }
                }
                IdleHandler -> {
                    current.metadata.asIdleHandler()!!.let {
                        idleHandlerName = it.javaClass.name ?: ""
                    }
                }
                NativeInput -> {
                    current.metadata.asMotionEvent()?.let {
                        isTouch = true
                        action = it.actionMasked
                        rawX = it.rawX
                        rawY = it.rawY
                        return
                    }
                    current.metadata.asKeyEvent()!!.let {
                        isTouch = false
                        action = it.action
                        keyCode = it.keyCode
                    }
                }
            }
        }
    }
}