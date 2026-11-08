package com.example.focustrace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usages",
    foreignKeys = [
        ForeignKey(
            entity = PhoneEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["sessionId"])
    ]
)
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val sessionId: Long,
    val packageName: String,
    val appLabel: String,
    val startAt: Long,
    val durationMs: Long,
    val isAllowed: Boolean,
    val isAuto: Boolean,
    val isCounted: Boolean
)
