package com.example.musiccapture

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_AUDIO = 101
        private const val REQUEST_CAPTURE = 102
        private const val REQUEST_IMPORT = 103
    }

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var autoStopCheck: CheckBox
    private lateinit var songStatusText: TextView
    private lateinit var historyButton: Button
    private lateinit var historyText: TextView
    private lateinit var importStatusText: TextView
    private var historyVisible = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.hasExtra(CaptureService.EXTRA_STATUS) == true) {
                val message = intent.getStringExtra(CaptureService.EXTRA_STATUS)
                    ?: "Durum bilgisi alınamadı."
                statusText.text = message
                if (message.contains("kaydedildi", ignoreCase = true) ||
                    message.contains("Yedek WAV", ignoreCase = true)) {
                    refreshHistory()
                }
            }
            if (intent?.hasExtra(CaptureService.EXTRA_PROGRESS) == true) {
                progressText.text = intent.getStringExtra(CaptureService.EXTRA_PROGRESS)
                    ?: "Akıllı bitiş: durum bilgisi bekleniyor"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 24)
        }

        val title = TextView(this).apply {
            text = "Dahili Ses Yakalama"
            textSize = 20f
        }
        val description = TextView(this).apply {
            text = "\nMikrofon değil, izin verilen dahili oynatma sesi kaydedilir.\n\n" +
                "1. Başlat'a bas.\n" +
                "2. Android'in izin ekranını onayla.\n" +
                "3. Başka bir uygulamada ses oynat.\n" +
                "4. Otomatik bitiş açıksa şarkı sonunda MP3 hazırlanır; " +
                "gerekirse Durdur / Kaydet'e bas.\n\n" +
                "Çıktı: Müzik/MusicCaptureTest klasöründe 320 kb/sn MP3.\n" +
                "Şarkı adı için aşağıdaki Bildirim erişimi ayarını aç.\n"
            textSize = 14f
        }
        statusText = TextView(this).apply {
            textSize = 18f
            text = "Henüz kayıt başlatılmadı."
            setPadding(4, 16, 4, 12)
        }

        autoStopCheck = CheckBox(this).apply {
            text = "Şarkı bitince otomatik durdur ve kaydet"
            textSize = 14f
            isChecked = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE)
                .getBoolean(CaptureService.PREF_AUTO_STOP, true)
            setOnCheckedChangeListener { _, enabled ->
                getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(CaptureService.PREF_AUTO_STOP, enabled).apply()
                if (!enabled) {
                    progressText.text = "Otomatik bitiş kapalı (sonraki kayıt için)"
                } else {
                    progressText.text = "Otomatik bitiş açık (sonraki kayıt için)"
                }
            }
        }
        progressText = TextView(this).apply {
            textSize = 14f
            setPadding(4, 8, 4, 6)
            text = "Şarkı süresi varsa otomatik sonlandırma yapılır."
        }

        val startButton = Button(this).apply {
            text = "Başlat"
            textSize = 12f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { checkPermissionAndStart() }
        }
        val stopButton = Button(this).apply {
            text = "Durdur / Kaydet"
            textSize = 12f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java)
                    .setAction(CaptureService.ACTION_STOP))
                statusText.text = "Kayıt durduruluyor, MP3 hazırlanıyor..."
            }
        }

        val songPermissionButton = Button(this).apply {
            text = "Şarkı adını etkinleştir (Bildirim erişimi)"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        songStatusText = TextView(this).apply {
            textSize = 14f
            setPadding(4, 8, 4, 8)
        }

        historyText = TextView(this).apply {
            textSize = 13f
            visibility = View.GONE
            setPadding(8, 12, 8, 18)
        }
        historyButton = Button(this).apply {
            text = "Kayıt geçmişi"
            textSize = 13f
            isAllCaps = false
            setOnClickListener {
                historyVisible = !historyVisible
                historyText.visibility = if (historyVisible) View.VISIBLE else View.GONE
                refreshHistory()
            }
        }

        importStatusText = TextView(this).apply {
            textSize = 13f
            setPadding(4, 4, 4, 8)
            text = "Eski kayıtların görünmemesi durumunda izin verip taratabilirsin."
        }
        val importButton = Button(this).apply {
            text = "Eski kayıtları tara"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { askImportPermission() }
        }

        // Put both compact controls at the very top, side by side, about half-width each.
        val density = resources.displayMetrics.density
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val compactHeight = (40f * density).toInt()
        val gap = (3f * density).toInt()
        buttons.addView(startButton, LinearLayout.LayoutParams(0, compactHeight, 1f).apply {
            setMargins(0, 0, gap, 0)
        })
        buttons.addView(stopButton, LinearLayout.LayoutParams(0, compactHeight, 1f).apply {
            setMargins(gap, 0, 0, 0)
        })
        layout.addView(buttons)
        layout.addView(autoStopCheck)
        layout.addView(progressText)
        layout.addView(statusText)
        layout.addView(songPermissionButton)
        layout.addView(songStatusText)
        layout.addView(historyButton)
        layout.addView(historyText)
        layout.addView(importButton)
        layout.addView(importStatusText)
        layout.addView(title)
        layout.addView(description)
        setContentView(ScrollView(this).apply { addView(layout) })
        // Index files created by previous APK versions on a worker thread.
        Thread {
            runCatching { RecordingHistory(this).use { it.importExistingRecordings() } }
            runOnUiThread { if (!isFinishing && !isDestroyed) refreshHistory() }
        }.start()
    }

    private fun refreshHistory() {
        runCatching {
            RecordingHistory(this).use { history ->
                historyButton.text = "Kayıt geçmişi (" + history.count() + ") " +
                    if (historyVisible) "▲" else "▼"
                if (historyVisible) {
                    historyText.text = "Son 20 kayıt:\n\n" + history.formattedHistory(20)
                }
            }
        }.onFailure {
            historyText.text = "Kayıt geçmişi okunamadı: " + (it.message ?: "bilinmeyen hata")
        }
    }

    private fun askImportPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            importOldFiles()
        } else {
            requestPermissions(arrayOf(permission), REQUEST_IMPORT)
        }
    }

    private fun importOldFiles() {
        importStatusText.text = "Eski kayıtlar taranıyor..."
        Thread {
            val result = runCatching {
                RecordingHistory(this).use { it.importExistingRecordings() }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    importStatusText.text = result.fold(
                        onSuccess = { "Tarama tamamlandı. Geçmişe eklenen eski dosya: " + it },
                        onFailure = { "Tarama başarısız: " + (it.message ?: "bilinmeyen hata") }
                    )
                    refreshHistory()
                }
            }
        }.start()
    }

    private fun checkPermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            askMediaProjectionConsent()
        }
    }

    private fun askMediaProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                askMediaProjectionConsent()
            } else {
                statusText.text = "Android ses yakalama için ses kayıt iznini zorunlu tutuyor."
            }
        } else if (requestCode == REQUEST_IMPORT) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                importOldFiles()
            } else {
                importStatusText.text = "Eski dosya tarama izni verilmedi. Yeni kayıtların geçmişi tutulmaya devam eder."
            }
        }
    }

    @Deprecated("Used for MediaProjection consent on Android 12")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Ekran/ses paylaşım izni verilmedi."
            return
        }
        val startIntent = Intent(this, CaptureService::class.java)
            .setAction(CaptureService.ACTION_START)
            .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            .putExtra(CaptureService.EXTRA_AUTO_STOP, autoStopCheck.isChecked)
        startForegroundService(startIntent)
        statusText.text = "Ses yakalama başlatılıyor..."
    }

    override fun onResume() {
        super.onResume()
        if (::historyButton.isInitialized) refreshHistory()
        val allowed = PlayingSong.hasPermission(this)
        val playing = if (allowed) PlayingSong.current(this) else null
        songStatusText.text = when {
            !allowed -> "Şarkı adı özelliği kapalı. Yukarıdaki düğmeyle 'Ses Test - Şarkı Adı' erişimini aç."
            playing != null -> "Algılanan şarkı: " + playing.displayName()
            else -> "Şarkı erişimi açık. Müzik çalarken başlık otomatik algılanacak."
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CaptureService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        val last = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE)
            .getString(CaptureService.EXTRA_STATUS, null)
        if (last != null) statusText.text = last
        val previousProgress = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE)
            .getString(CaptureService.EXTRA_PROGRESS, null)
        if (previousProgress != null) progressText.text = previousProgress
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }
}
