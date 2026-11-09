package com.example.focustrace.data.repo

import android.content.Context
import com.example.focustrace.FocusTraceApp

val Context.sessionRepository: SessionRepository
    get() = (applicationContext as FocusTraceApp).sessionRepository
