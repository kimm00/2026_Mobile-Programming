package com.example.focustrace.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.focustrace.databinding.ActivityPhotoReviewBinding

class PhotoReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoReviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPhotoReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.photoReviewRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnRetake.setOnClickListener {
            finish()
        }

        binding.btnUsePhoto.setOnClickListener {
            Toast.makeText(this, "Completion photo selected (Preview UI flow)", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }
}
