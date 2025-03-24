package com.xiaoxiaoying.timeflip.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xiaoxiaoying.example.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.timeFlipView.time = 12 * 60 * 60 * 1000

    }

}