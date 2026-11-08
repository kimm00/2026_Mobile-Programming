package com.example.focustrace.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refocus_alerts",
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
data class RefocusAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val firedAt: Long,
    val usedMsBeforeAlert: Long,
    val leftAt: Long? = null
)
