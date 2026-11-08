package com.example.focustrace.detection

import android.content.Context
import android.content.pm.PackageManager
import com.example.focustrace.data.db.dao.SessionDao
import com.example.focustrace.data.db.entity.AppUsageEntity
import com.example.focustrace.data.db.entity.PhoneEventEntity

class RoomEpisodeSink(
    private val context: Context,
    private val sessionId: Long,
    private val dao: SessionDao
) : EpisodeSink {

    override suspend fun onEpisode(result: EpisodeAnalysisResult) {
        if (!result.shouldSave) return

        val event = PhoneEventEntity(
            sessionId = sessionId,
            startAt = result.startAt,
            endAt = result.endAt,
            kind = result.kind
        )

        val usages = result.appUsages.map { usage ->
            AppUsageEntity(
                eventId = 0L,
                sessionId = sessionId,
                packageName = usage.packageName,
                appLabel = getAppLabel(usage.packageName),
                startAt = usage.startAt,
                durationMs = usage.durationMs,
                isAllowed = usage.isAllowed,
                isAuto = usage.isAuto,
                isCounted = usage.isCounted
            )
        }

        dao.insertEpisode(event, usages)
    }


    private fun getAppLabel(packageName: String): String {
        val knownApps = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.android.vending" to "Google Play Store",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.instagram.android" to "Instagram",
            "com.kakao.talk" to "KakaoTalk",
            "com.spotify.music" to "Spotify",
            "com.netflix.mediaclient" to "Netflix"
        )

        knownApps[packageName]?.let { return it }

        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
