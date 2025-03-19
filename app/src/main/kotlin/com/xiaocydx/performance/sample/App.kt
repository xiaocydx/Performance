package com.xiaocydx.performance.sample

import android.app.Application
import com.xiaocydx.performance.Performance

/**
 * @author xcc
 * @date 2025/3/19
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Performance.init()
    }
}