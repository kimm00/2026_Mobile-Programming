package com.example.focustrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private val reviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.cameraRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnFlash.setOnClickListener {
            Toast.makeText(this, "Flash toggle (UI sample)", Toast.LENGTH_SHORT).show()
        }

        binding.btnFlip.setOnClickListener {
            Toast.makeText(this, "Switch camera (UI sample)", Toast.LENGTH_SHORT).show()
        }

        binding.btnShutter.setOnClickListener {
            // Launch Photo Review Activity UI
            reviewLauncher.launch(Intent(this, PhotoReviewActivity::class.java))
        }
    }
}
