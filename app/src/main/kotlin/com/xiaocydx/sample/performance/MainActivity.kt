package com.xiaocydx.sample.performance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        click(R.id.btnFrameMetrics) { }
        click(R.id.btnBlockMetrics) { start<BlockMetricsActivity>() }
        click(R.id.btnANRMetrics) { }
        click(R.id.btnIdleHandler) { start<CheckIdleHandlerActivity>() }
    }

    private inline fun click(id: Int, crossinline action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { action() }
    }

    private inline fun <reified T : Activity> start() {
        startActivity(Intent(this, T::class.java))
    }
}