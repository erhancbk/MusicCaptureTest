package com.example.musiccapture

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.MediaStore
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local history of successfully saved MP3/WAV files, with a legacy-file import. */
class RecordingHistory(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "music_capture_history.db", null, 1) {

    private val applicationContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE recordings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL UNIQUE,
                title TEXT,
                artist TEXT,
                song_key TEXT,
                source_package TEXT,
                file_type TEXT NOT NULL,
                saved_at INTEGER NOT NULL,
                is_repeat INTEGER NOT NULL DEFAULT 0,
                imported INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX song_lookup ON recordings(song_key)")
        db.execSQL("CREATE INDEX saved_order ON recordings(saved_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add future schema migrations here.
    }

    data class Entry(
        val filename: String,
        val title: String?,
        val artist: String?,
        val timeMillis: Long,
        val isRepeat: Boolean,
        val imported: Boolean
    )

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        return decomposed.lowercase(Locale.ROOT)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim().replace(Regex("""\s+"""), " ")
    }

    private fun key(song: SongInfo?): String? {
        if (song == null || song.title.isBlank()) return null
        val title = normalize(song.title)
        if (title.isBlank()) return null
        return normalize(song.artist) + "|" + title
    }

    fun firstMatch(song: SongInfo?): Entry? {
        val k = key(song) ?: return null
        readableDatabase.query(
            "recordings",
            arrayOf("filename", "title", "artist", "saved_at", "is_repeat", "imported"),
            "song_key = ?",
            arrayOf(k),
            null, null, "saved_at ASC", "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Entry(
                filename = cursor.getString(0),
                title = cursor.getString(1),
                artist = cursor.getString(2),
                timeMillis = cursor.getLong(3),
                isRepeat = cursor.getInt(4) != 0,
                imported = cursor.getInt(5) != 0
            )
        }
    }

    fun addSaved(
        filename: String,
        song: SongInfo?,
        isMultipleSongs: Boolean,
        isRepeat: Boolean,
        imported: Boolean = false,
        savedAtMillis: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("filename", filename)
            put("title", if (isMultipleSongs) null as String? else song?.title)
            put("artist", if (isMultipleSongs) null as String? else song?.artist)
            put("song_key", if (isMultipleSongs) null as String? else key(song))
            put("source_package", song?.sourcePackage)
            put("file_type", filename.substringAfterLast('.', "").lowercase(Locale.ROOT))
            put("saved_at", savedAtMillis)
            put("is_repeat", if (isRepeat) 1 else 0)
            put("imported", if (imported) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "recordings", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    /** Remove a history entry only after its exact saved MediaStore file was deleted. */
    fun removeFile(filename: String): Int =
        writableDatabase.delete("recordings", "filename = ?", arrayOf(filename))

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM recordings", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun latest(limit: Int = 20): List<Entry> {
        val items = mutableListOf<Entry>()
        readableDatabase.query(
            "recordings",
            arrayOf("filename", "title", "artist", "saved_at", "is_repeat", "imported"),
            null, null, null, null, "saved_at DESC, id DESC",
            limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    Entry(
                        filename = cursor.getString(0),
                        title = cursor.getString(1),
                        artist = cursor.getString(2),
                        timeMillis = cursor.getLong(3),
                        isRepeat = cursor.getInt(4) != 0,
                        imported = cursor.getInt(5) != 0
                    )
                )
            }
        }
        return items
    }

    /**
     * Rebuilds history from older recordings already in Music/MusicCaptureTest,
     * including after uninstalling an APK signed with a different debug key.
     */
    fun importExistingRecordings(): Int {
        var importedCount = 0
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val resolver = applicationContext.contentResolver
        resolver.query(
            uri, projection,
            MediaStore.Audio.Media.RELATIVE_PATH + " = ?",
            arrayOf("Music/MusicCaptureTest/"),
            MediaStore.Audio.Media.DATE_ADDED + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (!name.endsWith(".mp3", true) && !name.endsWith(".wav", true)) continue
                if (hasFilename(name)) continue
                val song = songFromLegacyFilename(name)
                val isRepeat = firstMatch(song) != null
                val dateAdded = cursor.getLong(dateIndex) * 1000L
                addSaved(
                    filename = name, song = song, isMultipleSongs = song == null,
                    isRepeat = isRepeat, imported = true,
                    savedAtMillis = if (dateAdded > 0L) dateAdded else System.currentTimeMillis()
                )
                importedCount++
            }
        }
        return importedCount
    }

    private fun hasFilename(filename: String): Boolean {
        readableDatabase.query(
            "recordings", arrayOf("id"), "filename = ?",
            arrayOf(filename), null, null, null, "1"
        ).use { return it.moveToFirst() }
    }

    private fun songFromLegacyFilename(filename: String): SongInfo? {
        val raw = filename.substringBeforeLast('.')
            .replace(Regex("""_\d{8}_\d{6}$"""), "")
        if (raw.startsWith("MusicCapture", ignoreCase = true)) return null
        val separator = raw.indexOf(" - ")
        if (separator <= 0 || separator + 3 >= raw.length) return null
        val artist = raw.substring(0, separator).trim()
        val title = raw.substring(separator + 3).trim()
        return if (title.isNotBlank()) SongInfo(title, artist, "legacy_import") else null
    }

    fun formattedHistory(limit: Int = 20): String {
        val records = latest(limit)
        if (records.isEmpty()) return "Henüz kaydedilmiş dosya bulunmuyor."
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return records.joinToString("\n\n") { entry ->
            val label = when {
                entry.isRepeat -> "⚠ Tekrar"
                entry.imported -> "↩ Önceki kayıt"
                else -> "✓ Kaydedildi"
            }
            val title = if (!entry.title.isNullOrBlank()) {
                (entry.artist?.takeIf { it.isNotBlank() }?.plus(" - ") ?: "") + entry.title
            } else {
                entry.filename.substringBeforeLast(".")
            }
            label + " • " + formatter.format(Date(entry.timeMillis)) +
                "\n" + title + "\n" + entry.filename
        }
    }
}
