package com.example.focustrace.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appLabel: String,
    val addedAt: Long
)
