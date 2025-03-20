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

package com.xiaocydx.performance.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import com.xiaocydx.performance.assertMainThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * @author xcc
 * @date 2025/3/20
 */
internal class ActivityManager {
    private val list = mutableListOf<Activity>()
    private val _event = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val event = _event.asSharedFlow()

    @MainThread
    fun init(application: Application) {
        assertMainThread()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    list.add(activity)
                    _event.tryEmit(ActivityEvent.Created(activity))
                }

                override fun onActivityStarted(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Started(activity))
                }

                override fun onActivityResumed(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Resumed(activity))
                }

                override fun onActivityPaused(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Paused(activity))
                }

                override fun onActivityStopped(activity: Activity) {
                    _event.tryEmit(ActivityEvent.Stopped(activity))
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                }

                override fun onActivityDestroyed(activity: Activity) {
                    list.remove(activity)
                    _event.tryEmit(ActivityEvent.Destroyed(activity))
                }
            }
        )
    }

    @MainThread
    fun getLastActivity(): Activity? {
        assertMainThread()
        return list.lastOrNull()
    }
}

internal sealed class ActivityEvent(activity: Activity) {
    val clazz = activity::class.java

    class Created(activity: Activity) : ActivityEvent(activity)
    class Started(activity: Activity) : ActivityEvent(activity)
    class Resumed(activity: Activity) : ActivityEvent(activity)
    class Paused(activity: Activity) : ActivityEvent(activity)
    class Stopped(activity: Activity) : ActivityEvent(activity)
    class Destroyed(activity: Activity) : ActivityEvent(activity)
}