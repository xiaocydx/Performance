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

import android.view.Choreographer
import androidx.annotation.MainThread
import com.xiaocydx.performance.fake.FakeChoreographer
import com.xiaocydx.performance.runtime.assertMainThread
import com.xiaocydx.performance.runtime.looper.DispatchContext
import com.xiaocydx.performance.runtime.looper.End
import com.xiaocydx.performance.runtime.looper.LooperCallback
import com.xiaocydx.performance.runtime.looper.Scene
import com.xiaocydx.performance.runtime.looper.Start

/**
 * @author xcc
 * @date 2025/4/7
 */
@MainThread
internal class ChoreographerFrameInfo {
    private val markInputStart = MarkInputStart()
    private val markAnimationStart = MarkAnimationStart()
    private val markTraversalStart = MarkTraversalStart()
    private val postMarkStartActions = PostMarkStartActions()
    private var isPostMarkStartActions = false
    private var isDoFrameMessage = false
    private var fake: FakeChoreographer? = null
    private var doOnFrameEnd: (() -> Unit)? = null

    var isAvailable = false; private set
    var frameStartNanos = 0L; private set
    var inputStartNanos = 0L; private set
    var animationStartNanos = 0L; private set
    var traversalStartNanos = 0L; private set
    var frameEndNanos = 0L; private set
    val callback: LooperCallback = DoFrameCallback()

    fun init() = apply {
        assertMainThread()
        if (isAvailable) return@apply
        val fake = FakeChoreographer(Choreographer.getInstance())
        if (!fake.checkStubAvailable()) return@apply
        isAvailable = true
        this.fake = fake
    }

    fun doOnFrameEnd(action: (() -> Unit)? = null) {
        assertMainThread()
        doOnFrameEnd = action
        if (action != null) postMarkStartActions()
    }

    private fun markFrameStart() {
        isDoFrameMessage = false
        frameStartNanos = System.nanoTime()
    }

    private fun markInputStart() {
        isDoFrameMessage = true
        isPostMarkStartActions = false
        inputStartNanos = System.nanoTime()
    }

    private fun markAnimationStart() {
        animationStartNanos = System.nanoTime()
    }

    private fun markTraversalStart() {
        traversalStartNanos = System.nanoTime()
    }

    private fun markFrameEnd() {
        frameEndNanos = System.nanoTime()
        if (isDoFrameMessage && doOnFrameEnd != null) {
            postMarkStartActions()
            doOnFrameEnd?.invoke()
        }
    }

    private fun postMarkStartActions() {
        if (!isAvailable) return
        if (isPostMarkStartActions) return
        val fake = fake ?: return
        isPostMarkStartActions = true
        fake.interceptSchedule(postMarkStartActions)
    }

    private inner class DoFrameCallback : LooperCallback {
        override fun dispatch(current: DispatchContext) {
            if (current.scene != Scene.Message) return
            when (current) {
                is Start -> markInputStart()
                is End -> markFrameEnd()
            }
        }
    }

    private inner class PostMarkStartActions : () -> Unit {
        override fun invoke() {
            val fake = fake ?: return
            val atFrontOfQueue = true
            fake.postInputCallback(atFrontOfQueue, markInputStart)
            fake.postAnimationCallback(atFrontOfQueue, markAnimationStart)
            fake.postTraversalCallback(atFrontOfQueue, markTraversalStart)
        }
    }

    private inner class MarkInputStart : Runnable {
        override fun run() = markInputStart()
    }

    private inner class MarkAnimationStart : Runnable {
        override fun run() = markAnimationStart()
    }

    private inner class MarkTraversalStart : Runnable {
        override fun run() = markTraversalStart()
    }
}