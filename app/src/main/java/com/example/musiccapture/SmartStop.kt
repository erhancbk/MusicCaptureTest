package com.example.musiccapture

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import java.util.Locale

/**
 * Android's public media-session metadata, NOT YouTube's private APIs.
 * Position and duration are optional: a source may not supply them.
 */
data class PlaybackSnapshot(
    val song: SongInfo,
    val state: Int,
    val durationMs: Long,
    val positionMs: Long
)

object MusicPlayback {
    fun snapshot(context: Context, preferredPackage: String? = null): PlaybackSnapshot? {
        if (!PlayingSong.hasPermission(context)) return null
        return try {
            val component = ComponentName(context, PlaybackNotificationListener::class.java)
            val controllers = context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(component)
            val eligible = controllers.filter { session ->
                val title = session.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: session.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                !title.isNullOrBlank() && session.playbackState != null
            }
            val controller = eligible.firstOrNull {
                it.packageName == preferredPackage &&
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: eligible.firstOrNull {
                it.packageName == preferredPackage
            } ?: eligible.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: return null

            val metadata = controller.metadata ?: return null
            val state = controller.playbackState ?: return null
            val title = (
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            )?.trim().orEmpty()
            if (title.isBlank()) return null
            val artist = (
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: ""
            ).trim()
            val duration = if (metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            } else -1L

            val basePosition = state.position
            var position = basePosition
            val updatedAt = state.lastPositionUpdateTime
            val now = SystemClock.elapsedRealtime()
            if (state.state == PlaybackState.STATE_PLAYING &&
                basePosition >= 0L && updatedAt > 0L &&
                now >= updatedAt && now - updatedAt < 30000L &&
                state.playbackSpeed > 0f && state.playbackSpeed <= 4f
            ) {
                position = basePosition + ((now - updatedAt) * state.playbackSpeed).toLong()
            }
            PlaybackSnapshot(
                song = SongInfo(title, artist, controller.packageName),
                state = state.state,
                durationMs = duration,
                positionMs = position
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Auto-stops one manually started capture after the observed song ends.
 * Does not control YouTube, suppress ads, or attempt DRM bypass.
 */
class SmartStopMonitor {
    var lockedSong: SongInfo? = null
        private set

    private var lockedAt = 0L
    private var lastGoodDuration = -1L

    data class Decision(
        val stopReason: String? = null,
        val progress: String
    )

    private fun sameTrack(a: SongInfo, b: SongInfo): Boolean {
        // Artists may arrive later; the title and source package are stable enough.
        return a.sourcePackage == b.sourcePackage &&
            a.title.trim().lowercase(Locale.ROOT) ==
            b.title.trim().lowercase(Locale.ROOT)
    }

    private fun time(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1000L)
        return "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
    }

    fun evaluate(snapshot: PlaybackSnapshot?, elapsedNow: Long): Decision {
        if (snapshot == null) {
            return Decision(progress = if (lockedSong == null) {
                "Akıllı bitiş: şarkı ve süre bilgisi bekleniyor"
            } else {
                "Akıllı bitiş: oynatma bilgisi yok, elle durdurabilirsin"
            })
        }

        if (lockedSong == null) {
            if (snapshot.state != PlaybackState.STATE_PLAYING) {
                return Decision(progress = "Akıllı bitiş: şarkının başlaması bekleniyor")
            }
            lockedSong = snapshot.song
            lockedAt = elapsedNow
        }

        val locked = lockedSong!!
        if (!sameTrack(locked, snapshot.song)) {
            // Ignore very short transient metadata transitions when the player opens.
            if (elapsedNow - lockedAt >= 1200L) {
                return Decision(
                    stopReason = "Şarkı değişti veya reklam başladı; kayıt kaydediliyor",
                    progress = "Akıllı bitiş: şarkı değişimi algılandı"
                )
            }
            return Decision(progress = "Akıllı bitiş: şarkı tanımlanıyor")
        }

        // Some apps briefly omit duration; preserve the last credible value.
        if (snapshot.durationMs in 15000L..3600000L) {
            lastGoodDuration = snapshot.durationMs
        }
        val duration = lastGoodDuration
        val position = snapshot.positionMs
        val timeKnown = duration > 0 && position >= 0L
        val status = if (timeKnown) {
            "Akıllı bitiş: " + time(position.coerceAtMost(duration)) +
                " / " + time(duration) +
                " • kalan " + time((duration - position).coerceAtLeast(0L))
        } else {
            "Akıllı bitiş: süre/konum paylaşılmıyor; şarkı değişimi izleniyor"
        }

        if (!timeKnown) return Decision(progress = status)

        val nearEnd = position >= duration - 200L
        val fullyStopped = snapshot.state == PlaybackState.STATE_STOPPED ||
            snapshot.state == PlaybackState.STATE_NONE
        // Never stop a brand-new or paused song solely because metadata is stale.
        if (elapsedNow - lockedAt >= 800L &&
            ((snapshot.state == PlaybackState.STATE_PLAYING && nearEnd) ||
                (fullyStopped && position >= duration - 1200L))
        ) {
            return Decision(
                stopReason = "Şarkı süresi tamamlandı; MP3 kaydediliyor",
                progress = status
            )
        }
        return Decision(progress = status)
    }
}
