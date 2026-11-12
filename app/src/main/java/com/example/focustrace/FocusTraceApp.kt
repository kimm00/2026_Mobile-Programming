package com.example.focustrace

import android.app.Application
import android.content.Context
import com.example.focustrace.data.db.AppDatabase
import com.example.focustrace.data.repo.ReportRepository
import com.example.focustrace.data.repo.RoomReportRepository
import com.example.focustrace.data.repo.RoomSessionRepository
import com.example.focustrace.data.repo.SessionRepository

class FocusTraceApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.get(this)
    }

    val sessionRepository: SessionRepository by lazy {
        RoomSessionRepository(database.sessionDao())
    }

    val reportRepository: ReportRepository by lazy {
        RoomReportRepository(database)
    }
}

val Context.sessionRepository: SessionRepository
    get() = (applicationContext as FocusTraceApp).sessionRepository

val Context.reportRepository: ReportRepository
    get() = (applicationContext as FocusTraceApp).reportRepository
