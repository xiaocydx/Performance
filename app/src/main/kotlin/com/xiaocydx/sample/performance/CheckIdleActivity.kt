package com.xiaocydx.sample.performance

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity

/**
 * @author xcc
 * @date 2025/3/20
 */
class CheckIdleActivity: AppCompatActivity() {
    private lateinit var view: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = View(this)
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        val animation = RotateAnimation(0f, 360f)
        animation.repeatCount = Animation.INFINITE
        view.startAnimation(animation)
    }

    override fun onPause() {
        super.onPause()
        view.animation.cancel()
    }
}