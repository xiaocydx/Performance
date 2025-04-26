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

package com.xiaocydx.performance.runtime.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * @author xcc
 * @date 2025/3/20
 */
internal class ActivityWatcher {
    private val map = HashMap<ActivityKey, Activity>()
    private val _event = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = Int.MAX_VALUE)
    private var latestKey: ActivityKey? = null

    val event = _event.asSharedFlow()

    @MainThread
    fun init(application: Application) {
        assertMainThread()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val event = ActivityEvent.Created(activity)
                    map[event.activityKey] = activity
                    _event.tryEmit(event)
                }

                override fun onActivityStarted(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Started(activity))
                }

                override fun onActivityResumed(activity: Activity) {
                    val event = ActivityEvent.Resumed(activity)
                    latestKey = event.activityKey
                    _event.tryEmit(event)
                }

                override fun onActivityPaused(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Paused(activity))
                }

                override fun onActivityStopped(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Stopped(activity))
                }

                override fun onActivityDestroyed(activity: Activity) {
                    val event = ActivityEvent.Destroyed(activity)
                    if (event.activityKey == latestKey) latestKey = null
                    map.remove(event.activityKey)
                    _event.tryEmit(event)
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
        )
    }

    @MainThread
    fun getActivity(key: ActivityKey): Activity? {
        assertMainThread()
        return map[key]
    }

    @MainThread
    fun getLatestActivity(): Activity? {
        assertMainThread()
        return latestKey?.let(::getActivity)
    }
}