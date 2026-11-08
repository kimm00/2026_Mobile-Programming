package com.example.focustrace.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["startAt"])
    ]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val plan: String? = null,
    val goalMinutes: Int,
    val startAt: Long,
    val endAt: Long? = null,
    val status: String,
    val stopReason: String? = null,
    val stopMemo: String? = null,
    val reflection: String? = null,
    val photoUri: String? = null,
    val suggestedMinutes: Int? = null,
    val acceptedAiSuggestion: Boolean = false,
    val isSample: Boolean = false,
    val focusMs: Long = 0L,
    val phoneUseMs: Long = 0L,
    val checkCount: Int = 0,
    val unlockOnlyCount: Int = 0,
    val longestFocusMs: Long = 0L
)
