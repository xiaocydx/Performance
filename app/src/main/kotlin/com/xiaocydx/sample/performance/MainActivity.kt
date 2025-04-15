package com.xiaocydx.sample.performance

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = View(this)
        view.setOnClickListener { startActivity(Intent(this, CheckIdleActivity::class.java)) }
        setContentView(view)
    }
}