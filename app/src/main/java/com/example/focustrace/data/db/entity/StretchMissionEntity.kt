package com.example.focustrace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stretch_missions",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"])
    ]
)
data class StretchMissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val targetReps: Int = 10,
    val doneReps: Int,
    val result: String
)
