package com.xiaocydx.performance.runtime.signal

import androidx.annotation.MainThread
import com.xiaocydx.performance.runtime.assertMainThread

/**
 * @author xcc
 * @date 2025/4/27
 */
object Signal {
    private val callbacks = mutableListOf<ANRCallback>()
    private val pendingCallbacks = mutableListOf<ANRCallback>()

    init {
        System.loadLibrary("performance_runtime")
    }

    @MainThread
    fun addANRCallback(callback: ANRCallback) {
        assertMainThread()
        val beforeEmpty: Boolean
        synchronized(callbacks) {
            if (callbacks.contains(callback)) return
            beforeEmpty = callbacks.isEmpty()
            callbacks.add(callback)
        }
        if (beforeEmpty) nativeRegister()
    }

    @MainThread
    fun removeANRCallback(callback: ANRCallback) {
        assertMainThread()
        val afterEmpty: Boolean
        synchronized(callbacks) {
            if (!callbacks.contains(callback)) return
            callbacks.remove(callback)
            afterEmpty = callbacks.isEmpty()
        }
        if (afterEmpty) nativeUnregister()
    }

    @JvmStatic
    private external fun nativeRegister()

    @JvmStatic
    private external fun nativeUnregister()

    // Called from native code
    @JvmStatic
    private fun anr() {
        println("test -> 触发ANR回调")
        // synchronized(callbacks) {
        //     pendingCallbacks.addAll(callbacks)
        // }
        // pendingCallbacks.forEach { it.maybeANR() }
        // pendingCallbacks.clear()
    }
}

fun interface ANRCallback {
    fun maybeANR()
}