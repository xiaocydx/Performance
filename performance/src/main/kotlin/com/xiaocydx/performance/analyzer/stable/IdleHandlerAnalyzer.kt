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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.xiaocydx.performance.analyzer.stable

import android.os.Looper
import android.os.MessageQueue
import androidx.appcompat.app.AlertDialog
import com.xiaocydx.performance.Host
import com.xiaocydx.performance.analyzer.Analyzer
import com.xiaocydx.performance.runtime.component.ActivityEvent
import com.xiaocydx.performance.runtime.component.ActivityKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * @author xcc
 * @date 2025/3/20
 */
internal class IdleHandlerAnalyzer(host: Host) : Analyzer(host) {

    override fun init() {
        coroutineScope.launch {
            var checkKey: ActivityKey? = null
            var checkJob: Job? = null
            host.activityEvent.collect {
                when (it) {
                    is ActivityEvent.Created,
                    is ActivityEvent.Started -> return@collect
                    is ActivityEvent.Resumed -> {
                        checkKey = it.activityKey
                        checkJob?.cancel()
                        checkJob = launch {
                            val pass = withTimeoutOrNull(TIME_OUT_MILLIS) { awaitIdle() }
                            if (pass == null) showTimeoutDialog(checkKey)
                        }
                    }
                    is ActivityEvent.Paused,
                    is ActivityEvent.Stopped,
                    is ActivityEvent.Destroyed -> {
                        if (checkKey == it.activityKey) {
                            checkJob?.cancel()
                            checkJob = null
                        }
                    }
                }
            }
        }
    }

    private fun showTimeoutDialog(key: ActivityKey?) {
        val activity = key?.let(host::getActivity) ?: return
        AlertDialog.Builder(activity)
            .setTitle("AwaitActivityResumedIdle")
            .setMessage("timeout")
            .show()
    }

    private suspend fun awaitIdle() {
        suspendCancellableCoroutine { cont ->
            val idleHandler = MessageQueue.IdleHandler {
                cont.resume(Unit)
                false
            }
            val queue = Looper.myQueue()
            queue.addIdleHandler(idleHandler)
            cont.invokeOnCancellation { queue.removeIdleHandler(idleHandler) }
        }
    }

    private companion object {
        const val TIME_OUT_MILLIS = (10 * 1000L * 0.8).toLong()
    }
}