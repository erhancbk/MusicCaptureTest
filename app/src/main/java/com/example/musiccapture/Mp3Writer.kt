package com.example.musiccapture

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import io.vacco.libmp3lame.Jlame
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encodes the 44.1 kHz, 16-bit little-endian stereo PCM capture to a real MPEG Layer III file.
 * Runs after capture has stopped; never blocks AudioRecord's recording thread.
 */
data class SavedRecording(val filename: String, val uri: Uri)

object Mp3Writer {
    private const val SAMPLE_RATE = 44100
    private const val BITRATE_KBPS = 320
    private const val FOLDER = "Music/MusicCaptureTest"
    private const val READ_BYTES = 16384 // Exact multiple of 4 (stereo 16-bit sample frame)

    fun write(resolver: ContentResolver, pcm: File, song: SongInfo?, multipleSongs: Boolean): SavedRecording {
        val filename = PlayingSong.safeFilename(song, multipleSongs) + ".mp3"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MP3 için Müzik klasörüne dosya açılamadı.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                val gf = Jlame.lame_init()
                try {
                    check(!gf.lame_set_num_channels(2)) { "Stereo kodlayıcı ayarlanamadı." }
                    check(gf.lame_set_out_samplerate(SAMPLE_RATE) == 0) {
                        "MP3 örnekleme hızı ayarlanamadı."
                    }
                    check(gf.lame_set_brate(BITRATE_KBPS) == 0) {
                        "MP3 bit hızı ayarlanamadı."
                    }
                    gf.lame_set_VBR(Jlame.vbr_off)
                    gf.lame_set_quality(5)
                    gf.lame_set_bWriteVbrTag(false)
                    gf.lame_set_write_id3tag_automatic(false)
                    check(Jlame.lame_init_params(gf) == 0) {
                        "LAME MP3 kodlayıcı başlatılamadı."
                    }

                    val inputBytes = ByteArray(READ_BYTES + 4)
                    val pcmShorts = ShortArray((READ_BYTES + 4) / 2)
                    val encoded = ByteArray(65536)
                    var leftOver = 0
                    FileInputStream(pcm).use { input ->
                        while (true) {
                            val read = input.read(inputBytes, leftOver, READ_BYTES - leftOver)
                            if (read == -1) break
                            if (read == 0) continue
                            val available = leftOver + read
                            val aligned = available - (available % 4)
                            if (aligned > 0) {
                                ByteBuffer.wrap(inputBytes, 0, aligned)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .get(pcmShorts, 0, aligned / 2)
                                val encodedBytes = Jlame.lame_encode_buffer_interleaved(
                                    gf, pcmShorts, aligned / 4, encoded, 0, encoded.size
                                )
                                check(encodedBytes >= 0) {
                                    "MP3 kodlama hatası: $encodedBytes"
                                }
                                if (encodedBytes > 0) output.write(encoded, 0, encodedBytes)
                            }
                            leftOver = available - aligned
                            if (leftOver > 0) {
                                System.arraycopy(inputBytes, aligned, inputBytes, 0, leftOver)
                            }
                        }
                    }
                    check(leftOver == 0) { "Eksik PCM örnek verisi." }
                    val flushBytes = Jlame.lame_encode_flush(gf, encoded, encoded.size)
                    check(flushBytes >= 0) { "MP3 sonlandırma hatası: $flushBytes" }
                    if (flushBytes > 0) output.write(encoded, 0, flushBytes)
                } finally {
                    Jlame.lame_close(gf)
                }
            } ?: error("MP3 dosyası yazmaya açılamadı.")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) {
                "MP3 dosyası görünür hâle getirilemedi."
            }
            return SavedRecording(filename, uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
