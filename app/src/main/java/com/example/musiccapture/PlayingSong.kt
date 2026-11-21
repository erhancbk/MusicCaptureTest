package com.example.musiccapture

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * Cross-app MediaSession metadata requires explicit notification access.
 * No notification text is collected or uploaded.
 */
class PlaybackNotificationListener : NotificationListenerService()

data class SongInfo(val title: String, val artist: String, val sourcePackage: String) {
    fun displayName(): String = if (artist.isBlank()) title else "$artist - $title"
}

object PlayingSong {
    fun hasPermission(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val component = ComponentName(context, PlaybackNotificationListener::class.java)
        return manager.isNotificationListenerAccessGranted(component)
    }

    /** Only actively PLAYING metadata, never a guessed song title. */
    fun current(context: Context): SongInfo? {
        if (!hasPermission(context)) return null
        return try {
            val sessions = context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, PlaybackNotificationListener::class.java))
            val controller = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING &&
                    validTitle(it.metadata) != null
            } ?: return null
            val metadata = controller.metadata ?: return null
            val title = validTitle(metadata) ?: return null
            val artist = (
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: ""
            ).trim()
            SongInfo(title, artist, controller.packageName)
        } catch (_: Exception) {
            null
        }
    }

    private fun validTitle(data: MediaMetadata?): String? {
        if (data == null) return null
        val title = (
            data.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        )?.trim().orEmpty()
        if (title.isBlank() || title.equals("unknown", ignoreCase = true)) return null
        return title
    }

    /** A capture containing multiple titles receives a neutral multi-track label. */
    fun safeFilename(song: SongInfo?, multipleSongs: Boolean): String {
        val base = when {
            multipleSongs -> "MusicCapture_MultiTrack"
            song != null -> song.displayName()
            else -> "MusicCapture"
        }
        val cleaned = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.')
            .take(105)
            .ifBlank { "MusicCapture" }
        val suffix = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss", java.util.Locale.US
        ).format(java.util.Date())
        return cleaned + "_" + suffix
    }
}
