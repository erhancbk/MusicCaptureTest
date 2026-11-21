package com.example.musiccapture
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 private lateinit var status: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,48,32,24) }
  status=TextView(this).apply { text="Music Capture Test (WAV)\n\nBildirim erişimini açın, müzik başlatın ve kayda basın.";textSize=18f }
  root.addView(status)
  fun button(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label;setOnClickListener { action() } }) }
  button("1. Şarkı algılama izni") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
  button("2. Kaydı başlat") {
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),102)
   else capture()
  }
  button("3. Kaydı durdur") { startService(Intent(this,CaptureService::class.java).apply { action=CaptureService.STOP });status.text="Kayıt durduruldu. Music/MusicCaptureTest klasörünü kontrol edin." }
  setContentView(root)
 }
 private fun capture() { val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(mgr.createScreenCaptureIntent(),101) }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) { super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==102 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) capture() }
 @Deprecated("Android 10-12 compatibility")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==101 && resultCode==RESULT_OK && data!=null) {
   startForegroundService(Intent(this,CaptureService::class.java).apply { action=CaptureService.START;putExtra("resultCode",resultCode);putExtra("resultData",data) })
   status.text="Kayıt başlatıldı. Ses kaynağı izin veriyorsa WAV dosyası oluşturulur."
  }
 }
}