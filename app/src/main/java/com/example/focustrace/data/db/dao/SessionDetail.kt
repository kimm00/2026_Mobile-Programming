package com.example.focustrace.data.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity
import com.example.focustrace.data.db.entity.SessionEntity

data class PhoneEventWithUsages(
    @Embedded
    val event: PhoneEventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "eventId"
    )
    val usages: List<AppUsageEntity>
)

data class SessionDetail(
    @Embedded
    val session: SessionEntity,
    @Relation(
        entity = PhoneEventEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val events: List<PhoneEventWithUsages>
)
