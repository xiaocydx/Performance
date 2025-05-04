package com.xiaocydx.sample.performance

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * @author xcc
 * @date 2025/5/4
 */
class ANRMetricsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anr_metrics)
        val button = findViewById<View>(R.id.button)
        button.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Thread.sleep(6000)
            }
            true
        }
    }
}