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

package com.xiaocydx.performance.fake

import android.view.Choreographer

/**
 * @author xcc
 * @date 2025/4/7
 */
internal class FakeChoreographer(private val choreographer: Choreographer) {
    private val mLock = mLockField?.get(choreographer)

    fun checkStubAvailable(): Boolean {
        // 仅支持Android 9.0以下Stub调用
        return mLock != null && mFrameScheduledField != null
    }

    /**
     * [action]调用[postInputCallback]、[postAnimationCallback]、[postTraversalCallback]，不申请Vsync
     *
     * ```
     * public final class Choreographer {
     *     private boolean mFrameScheduled;
     *
     *     private void postCallbackDelayedInternal(...) {
     *         ...
     *         scheduleFrameLocked(now);
     *         ...
     *     }
     *
     *     private void scheduleFrameLocked(long now) {
     *         if (!mFrameScheduled) { // 反射改为true，拦截申请Vsync
     *            mFrameScheduled = true;
     *            ...
     *         }
     *     }
     * }
     * ```
     */
    fun interceptSchedule(action: () -> Unit) {
        synchronized(mLock!!) {
            val prev = mFrameScheduledField!!.get(choreographer) as Boolean
            if (!prev) mFrameScheduledField.set(choreographer, true)
            action()
            if (!prev) mFrameScheduledField.set(choreographer, false)
        }
    }

    fun postInputCallback(atFrontOfQueue: Boolean, action: Runnable) {
        postCallback(CALLBACK_INPUT, action, atFrontOfQueue)
    }

    fun postAnimationCallback(atFrontOfQueue: Boolean, action: Runnable) {
        postCallback(CALLBACK_ANIMATION, action, atFrontOfQueue)
    }

    fun postTraversalCallback(atFrontOfQueue: Boolean, action: Runnable) {
        postCallback(CALLBACK_TRAVERSAL_BELOW_R, action, atFrontOfQueue)
    }

    private fun postCallback(callbackType: Int, action: Runnable, atFrontOfQueue: Boolean) {
        val delayMillis = if (atFrontOfQueue) -1L else 0L
        choreographer.postCallbackDelayed(callbackType, action, null, delayMillis)
    }

    @Suppress("PrivateApi")
    companion object {
        private const val CALLBACK_INPUT = 0
        private const val CALLBACK_ANIMATION = 1
        private const val CALLBACK_TRAVERSAL_BELOW_R = 2 // Android 11以下类型值为2

        private val mLockField = try {
            Choreographer::class.java.getDeclaredField("mLock")
        } catch (_: Throwable) {
            null
        }

        private val mFrameScheduledField = try {
            Choreographer::class.java.getDeclaredField("mFrameScheduled")
        } catch (_: Throwable) {
            null
        }

        init {
            mLockField?.isAccessible = true
            mFrameScheduledField?.isAccessible = true
        }
    }
}