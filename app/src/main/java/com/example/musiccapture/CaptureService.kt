package com.example.musiccapture

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Records permitted playback audio from other apps, NOT the microphone.
 * Apps that block Android AudioPlaybackCapture will produce silence.
 */
class CaptureService : Service() {
    companion object {
        const val ACTION_START = "com.erhancbk.sestest.START"
        const val ACTION_STOP = "com.erhancbk.sestest.STOP"
        const val ACTION_STATUS = "com.erhancbk.sestest.STATUS"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_STATUS = "status"
        const val PREFS = "capture_status"
        private const val CHANNEL = "internal_audio_capture"
        private const val NOTIFICATION_ID = 81
        private const val DUPLICATE_CHANNEL = "song_duplicate_warning"
        private const val DUPLICATE_NOTIFICATION_ID = 82
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 2
    }

    @Volatile private var shouldRecord = false
    @Volatile private var activeRecorder: AudioRecord? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL, "Dahili ses yakalama", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val duplicateChannel = NotificationChannel(
            DUPLICATE_CHANNEL, "Tekrar kaydedilen şarkılar", NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(duplicateChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (shouldRecord) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            report("Kayıt izni alınmadı.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                notification("Dahili ses kaydı hazırlanıyor"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } catch (e: Exception) {
            report("Ön plan hizmeti başlatılamadı: " + (e.message ?: "bilinmeyen hata"))
            stopSelf()
            return START_NOT_STICKY
        }

        shouldRecord = true
        worker = Thread({ recordPlayback(resultCode, resultData) }, "InternalAudioCapture")
        worker?.start()
        return START_NOT_STICKY
    }

    private fun notification(description: String): Notification {
        val stopIntent = Intent(this, CaptureService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Ses Test - dahili ses")
            .setContentText(description)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Durdur", stopPending)
            .build()
    }

    private fun warnPreviouslySaved(song: SongInfo, match: RecordingHistory.Entry) {
        val message = "⚠ Daha önce kaydedildi: " + song.displayName() +
            ". Önceki dosya: " + match.filename + ". Kayıt devam ediyor."
        report(message)
        val openPending = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alert = Notification.Builder(this, DUPLICATE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Bu şarkı daha önce kaydedildi")
            .setContentText(song.displayName())
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .build()
        // Notification permission/settings may block alerts. Recording must never stop.
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(DUPLICATE_NOTIFICATION_ID, alert)
            manager.notify(NOTIFICATION_ID, notification("Tekrar bulundu. Kayıt devam ediyor."))
        }
    }

    private fun report(message: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(EXTRA_STATUS, message).apply()
        sendBroadcast(
            Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, message)
        )
    }

    private fun requestStop() {
        shouldRecord = false
        try {
            activeRecorder?.stop()
        } catch (_: Exception) {
            // The worker handles finalization.
        }
        if (worker == null) stopSelf()
    }

    private fun recordPlayback(resultCode: Int, resultData: Intent) {
        var projection: MediaProjection? = null
        var projectionCallback: MediaProjection.Callback? = null
        var tempFile: File? = null
        var bytesWritten = 0L
        var signalDetected = false
        val seenSongs = linkedMapOf<String, SongInfo>()
        var lastSongCheck = 0L
        var duplicateDetected = false
        val history = RecordingHistory(this)
        // Recover names from older MP3 files even when upgrading requires an uninstall.
        runCatching { history.importExistingRecordings() }

        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                ?: error("Android ses paylaşımını başlatamadı.")
            projection = mediaProjection

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    requestStop()
                }
            }
            projectionCallback = callback
            mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))

            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            check(minSize > 0) { "Desteklenen ses tamponu bulunamadı." }

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setBufferSizeInBytes(maxOf(minSize * 2, 16384))
                .build()
            activeRecorder = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord başlatılamadı."
            }

            val recordingFile = File.createTempFile("captured_", ".pcm", cacheDir)
            tempFile = recordingFile
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Ses kaydı başlatılamadı."
            }

            report("Kayıt sürüyor. Bir şarkı çal, sonra Durdur / Kaydet seç.")
            val buffer = ByteArray(16384)
            FileOutputStream(recordingFile).use { output ->
                while (shouldRecord) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastSongCheck >= 1000L) {
                        lastSongCheck = now
                        PlayingSong.current(this)?.let { song ->
                            val key = song.sourcePackage + "|" + song.title + "|" + song.artist
                            if (!seenSongs.containsKey(key)) {
                                seenSongs[key] = song
                                runCatching { history.firstMatch(song) }.getOrNull()?.let { match ->
                                    duplicateDetected = true
                                    warnPreviouslySaved(song, match)
                                }
                            }
                        }
                    }
                    val count = recorder.read(
                        buffer, 0, buffer.size, AudioRecord.READ_BLOCKING
                    )
                    if (count < 0) {
                        if (!shouldRecord) break
                        error("Ses okuma hatası: " + count)
                    }
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    bytesWritten += count
                    if (!signalDetected) {
                        var index = 0
                        while (index + 1 < count) {
                            val sample = ((buffer[index + 1].toInt() shl 8) or
                                (buffer[index].toInt() and 0xff)).toShort().toInt()
                            if (abs(sample) > 128) {
                                signalDetected = true
                                break
                            }
                            index += 2
                        }
                    }
                }
            }

            if (bytesWritten <= 0L) {
                report("Ses verisi gelmedi; dosya oluşturulmadı.")
            } else {
                PlayingSong.current(this)?.let { song ->
                    seenSongs[song.sourcePackage + "|" + song.title + "|" + song.artist] = song
                }
                val hasMultipleSongs = seenSongs.size > 1
                val songForFilename = if (seenSongs.size == 1) seenSongs.values.first() else null
                val alreadySaved = songForFilename != null &&
                    runCatching { history.firstMatch(songForFilename) != null }.getOrDefault(false)
                val isRepeat = duplicateDetected || alreadySaved
                report("Kayıt MP3'e dönüştürülüyor (320 kb/sn)...")
                val filename = try {
                    Mp3Writer.write(contentResolver, recordingFile, songForFilename, hasMultipleSongs)
                } catch (conversionError: Exception) {
                    // Preserve the recording instead of silently discarding it on encoder failure.
                    val backupFile = saveWav(recordingFile, bytesWritten)
                    runCatching {
                        history.addSaved(backupFile, songForFilename, hasMultipleSongs, isRepeat)
                    }
                    report(
                        "MP3 dönüşümü başarısız: " +
                        (conversionError.message ?: conversionError.javaClass.simpleName) +
                        ". Yedek WAV: Müzik/MusicCaptureTest/" + backupFile
                    )
                    return
                }
                val logged = runCatching {
                    history.addSaved(filename, songForFilename, hasMultipleSongs, isRepeat)
                }.isSuccess
                if (signalDetected) {
                    val suffix = when {
                        hasMultipleSongs -> " (Birden fazla şarkı algılandı; kayıt tek dosya.)"
                        songForFilename == null -> " (Şarkı adı bulunamadı; bildirim erişimini kontrol et.)"
                        else -> ""
                    }
                    val duplicateLabel = if (isRepeat) " ⚠ Tekrar kayıt." else ""
                    val logLabel = if (!logged) " (Geçmişe yazılamadı.)" else ""
                    report("MP3 kaydedildi: Müzik/MusicCaptureTest/" + filename +
                        duplicateLabel + suffix + logLabel)
                } else {
                    report(
                        "Sessiz MP3 kaydı: " + filename +
                        ". Kaynak uygulama dahili ses yakalamayı engelliyor olabilir."
                    )
                }
            }
        } catch (e: Exception) {
            report("Kayıt hatası: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            shouldRecord = false
            try { activeRecorder?.stop() } catch (_: Exception) {}
            try { activeRecorder?.release() } catch (_: Exception) {}
            activeRecorder = null
            if (projection != null && projectionCallback != null) {
                try { projection.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            }
            try { projection?.stop() } catch (_: Exception) {}
            tempFile?.delete()
            worker = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun saveWav(pcmFile: File, pcmLength: Long): String {
        check(pcmLength + 36 <= 0xFFFFFFFFL) { "WAV boyutu üst sınırı aşıldı." }
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "MusicCapture_" + date + ".wav"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MusicCaptureTest")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Müzik klasörüne dosya oluşturulamadı.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(createWavHeader(pcmLength))
                FileInputStream(pcmFile).use { input -> input.copyTo(output) }
            } ?: error("Kayıt dosyası açılamadı.")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return filename
    }

    private fun createWavHeader(pcmLength: Long): ByteArray {
        val bytesPerSample = 2
        val blockAlign = CHANNEL_COUNT * bytesPerSample
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((pcmLength + 36).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(CHANNEL_COUNT.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort((bytesPerSample * 8).toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmLength.toInt())
        return buffer.array()
    }

    override fun onDestroy() {
        requestStop()
        super.onDestroy()
    }
}
