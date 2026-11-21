package com.example.musiccapture

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_AUDIO = 101
        private const val REQUEST_CAPTURE = 102
    }

    private lateinit var statusText: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra(CaptureService.EXTRA_STATUS)
                ?: "Durum bilgisi alınamadı."
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
                "1. Yakalamayı Başlat'a bas.\n" +
                "2. Android'in izin ekranını onayla.\n" +
                "3. Başka bir uygulamada ses oynat.\n" +
                "4. Ses Test'e dön ve Durdur'a bas.\n\n" +
                "Çıktı: Müzik/MusicCaptureTest klasöründe 192 kb/sn MP3.\n"
            textSize = 14f
        }
        statusText = TextView(this).apply {
            textSize = 18f
            text = "Henüz kayıt başlatılmadı."
            setPadding(4, 16, 4, 12)
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
        layout.addView(statusText)
        layout.addView(title)
        layout.addView(description)
        setContentView(layout)
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
        startForegroundService(startIntent)
        statusText.text = "Ses yakalama başlatılıyor..."
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
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }
}
