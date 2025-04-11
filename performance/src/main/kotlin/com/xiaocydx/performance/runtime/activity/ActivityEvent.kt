package com.xiaocydx.performance.runtime.activity

import android.app.Activity

/**
 * @author xcc
 * @date 2025/3/27
 */
internal sealed class ActivityEvent(activity: Activity) {
    val activityKey = activity.hashCode()
    val activityClass = activity::class.java

    class Created(activity: Activity) : ActivityEvent(activity)
    class Started(activity: Activity) : ActivityEvent(activity)
    class Resumed(activity: Activity) : ActivityEvent(activity)
    class Paused(activity: Activity) : ActivityEvent(activity)
    class Stopped(activity: Activity) : ActivityEvent(activity)
    class Destroyed(activity: Activity) : ActivityEvent(activity)
}