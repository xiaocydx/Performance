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

package com.xiaocydx.performance.looper

import android.os.MessageQueue
import androidx.appcompat.app.AlertDialog
import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.activity.ActivityEvent
import com.xiaocydx.performance.assertMainThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * @author xcc
 * @date 2025/3/20
 */
internal class MainLooperIdleCheck(private val host: Performance.Host) {

    suspend fun repeatCheckOnActivityResumed() = withContext<Unit>(host.mainDispatcher) {
        var checkJob: Job? = null
        host.activityEvent.collect {
            // FIXME: 消除不同activity事件的影响
            val resumed = it as? ActivityEvent.Resumed
            when {
                checkJob == null && resumed != null -> {
                    checkJob = launch {
                        val pass = withTimeoutOrNull(TIME_OUT_MS) { awaitIdle() }
                        if (pass == null) showTimeoutDialog()
                    }
                }
                checkJob != null && resumed == null -> {
                    checkJob?.cancel()
                    checkJob = null
                }
            }
        }
    }

    private fun showTimeoutDialog() {
        val activity = host.getLastActivity() ?: return
        AlertDialog.Builder(activity)
            .setTitle("AwaitMainLooperIdle")
            .setMessage("timeout")
            .show()
    }

    private suspend fun awaitIdle() {
        suspendCancellableCoroutine { cont ->
            val idleHandler = MessageQueue.IdleHandler {
                cont.resume(Unit)
                false
            }
            host.mainLooper.queue.addIdleHandler(idleHandler)
            cont.invokeOnCancellation {
                assertMainThread()
                host.mainLooper.queue.removeIdleHandler(idleHandler)
            }
        }
    }

    private companion object {
        const val TIME_OUT_MS = 10 * 1000L
    }
}