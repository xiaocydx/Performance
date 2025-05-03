package com.xiaocydx.sample.performance

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * @author xcc
 * @date 2025/5/4
 */
class BlockMetricsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_metrics)
        val button = findViewById<View>(R.id.button)
        button.setOnClickListener { TestRunner().run() }
    }

    private class TestRunner {

        fun run() {
            Thread.sleep(480)
            A()
        }

        private fun A() {
            B()
            C()
        }

        private fun B() {
            Thread.sleep(120)
        }

        private fun C() {
            Thread.sleep(150)
        }
    }
}