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

package com.xiaocydx.performance.runtime.component

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread

/**
 * @author xcc
 * @date 2025/3/20
 */
internal class ComponentWatcher {
    private val activityMap = HashMap<ActivityKey, Activity>()
    private var latestActivityKey: ActivityKey? = null
    @Volatile private var activeActivityCount = 0

    @MainThread
    fun init(application: Application, send: (ActivityEvent) -> Unit) {
        assertMainThread()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val event = ActivityEvent.Created(activity)
                    activityMap[event.activityKey] = activity
                    send(event)
                }

                override fun onActivityStarted(activity: Activity) {
                    activeActivityCount++
                    send(ActivityEvent.Started(activity))
                }

                override fun onActivityResumed(activity: Activity) {
                    val event = ActivityEvent.Resumed(activity)
                    latestActivityKey = event.activityKey
                    send(event)
                }

                override fun onActivityPaused(activity: Activity) {
                    send(ActivityEvent.Paused(activity))
                }

                override fun onActivityStopped(activity: Activity) {
                    activeActivityCount--
                    send(ActivityEvent.Stopped(activity))
                }

                override fun onActivityDestroyed(activity: Activity) {
                    val event = ActivityEvent.Destroyed(activity)
                    if (event.activityKey == latestActivityKey) latestActivityKey = null
                    activityMap.remove(event.activityKey)
                    send(event)
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
        )
    }

    @MainThread
    fun getActivity(key: ActivityKey): Activity? {
        assertMainThread()
        return activityMap[key]
    }

    @MainThread
    fun getLatestActivity(): Activity? {
        assertMainThread()
        return latestActivityKey?.let(::getActivity)
    }

    @AnyThread
    fun getActiveActivityCount(): Int{
        return activeActivityCount
    }
}