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

package com.xiaocydx.performance.analyzer

import android.view.View
import androidx.core.view.ViewCompat

internal inline fun View.doOnAttach(
    crossinline action: (view: View) -> Unit
): OneShotAttachStateListener? {
    return if (ViewCompat.isAttachedToWindow(this)) {
        action(this)
        null
    } else {
        OneShotAttachStateListener(this, isAttach = true) { action(it) }
    }
}

internal inline fun View.doOnDetach(
    crossinline action: (view: View) -> Unit
): OneShotAttachStateListener? {
    return if (!ViewCompat.isAttachedToWindow(this)) {
        action(this)
        null
    } else {
        OneShotAttachStateListener(this, isAttach = false) { action(it) }
    }
}

internal class OneShotAttachStateListener(
    private val view: View,
    private val isAttach: Boolean,
    private val action: (view: View) -> Unit
) : View.OnAttachStateChangeListener {

    init {
        view.addOnAttachStateChangeListener(this)
    }

    override fun onViewAttachedToWindow(view: View) {
        if (isAttach) complete()
    }

    override fun onViewDetachedFromWindow(view: View) {
        if (!isAttach) complete()
    }

    private fun complete() {
        removeListener()
        action(view)
    }

    fun removeListener() {
        view.removeOnAttachStateChangeListener(this)
    }
}