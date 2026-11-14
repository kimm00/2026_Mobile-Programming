package com.example.focustrace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.entity.AiInsightEntity
import com.example.focustrace.data.db.entity.AllowedAppEntity
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.DeviceStateEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity
import com.example.focustrace.data.db.entity.RefocusAlertEntity
import com.example.focustrace.data.db.entity.SessionEntity
import com.example.focustrace.data.db.entity.StretchMissionEntity

@Database(
    entities = [
        SessionEntity::class,
        PhoneEventEntity::class,
        AppUsageEntity::class,
        AllowedAppEntity::class,
        RefocusAlertEntity::class,
        StretchMissionEntity::class,
        AiInsightEntity::class,
        DeviceStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focustrace.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
