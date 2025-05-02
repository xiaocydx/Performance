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

/**
 * @author xcc
 * @date 2025/3/27
 */
internal sealed class ActivityEvent(activity: Activity) {
    val activityKey = ActivityKey(activity)
    val activityClass = activity::class.java

    class Created(activity: Activity) : ActivityEvent(activity)
    class Started(activity: Activity) : ActivityEvent(activity)
    class Resumed(activity: Activity) : ActivityEvent(activity)
    class Paused(activity: Activity) : ActivityEvent(activity)
    class Stopped(activity: Activity) : ActivityEvent(activity)
    class Destroyed(activity: Activity) : ActivityEvent(activity)
}