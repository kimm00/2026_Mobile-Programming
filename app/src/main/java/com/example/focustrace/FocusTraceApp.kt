package com.example.focustrace

import android.app.Application
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.repo.SessionRepository
import com.example.focustrace.data.repo.SessionRepositoryImpl

class FocusTraceApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.get(this)
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(database.sessionDao())
    }
}
