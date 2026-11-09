package com.example.focustrace.detection

import android.content.pm.PackageManager
import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomEpisodeSink(
    private val sessionDao: SessionDao,
    private val sessionId: Long,
    scope: CoroutineScope,
    private val packageManager: PackageManager,
) : EpisodeSink {

    override suspend fun onEpisode(result: EpisodeAnalysisResult) {
        withContext(Dispatchers.IO) {
            val phoneEvent = PhoneEventEntity(
                sessionId = sessionId,
                startAt = result.startAt,
                endAt = result.endAt,
                kind = result.kind,
            )

            val appUsages = result.appUsages.map { usage ->
                val appLabel = try {
                    val appInfo = packageManager.getApplicationInfo(usage.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    usage.packageName
                }

                AppUsageEntity(
                    eventId = 0L,
                    sessionId = sessionId,
                    packageName = usage.packageName,
                    appLabel = appLabel,
                    startAt = usage.startAt,
                    durationMs = usage.durationMs,
                    isAllowed = usage.isAllowed,
                    isAuto = usage.isAuto,
                    isCounted = usage.isCounted,
                )
            }

            sessionDao.insertEpisode(phoneEvent, appUsages)
        }
    }
}
