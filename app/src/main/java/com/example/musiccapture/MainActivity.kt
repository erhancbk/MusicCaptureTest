package com.example.musiccapture
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 private lateinit var status: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,48,32,24) }
  status=TextView(this).apply { text="Music Capture Test v0.2\nDahili ses kaydı testi (WAV). Bildirim erişimi istemez.";textSize=18f }
  root.addView(status)
  root.addView(Button(this).apply { text="Kaydı başlat";setOnClickListener {
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),102) else capture()
  } })
  root.addView(Button(this).apply { text="Kaydı durdur";setOnClickListener {
   startService(Intent(this@MainActivity,CaptureService::class.java).apply { action=CaptureService.STOP })
   status.text="Kayıt durduruldu. Music/MusicCaptureTest klasörüne bakın."
  } })
  setContentView(root)
 }
 private fun capture() { val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(mgr.createScreenCaptureIntent(),101) }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==102 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) capture() }
 @Deprecated("Android 12 compatibility")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==101 && resultCode==RESULT_OK && data!=null) {
   startForegroundService(Intent(this,CaptureService::class.java).apply { action=CaptureService.START;putExtra("resultCode",resultCode);putExtra("resultData",data) })
   status.text="Kayıt başladı. Kaynak uygulama izin verirse WAV dosyası oluşturulur."
  }
 }
}