package com.example.musiccapture

import android.app.*
import android.content.*
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class CaptureService : Service() {
 companion object { const val START="capture.start"; const val STOP="capture.stop"; const val TRACK="capture.track";private const val CHANNEL="capture_channel" }
 @Volatile private var running=false
 private var projection:MediaProjection?=null
 private var recorder:AudioRecord?=null
 private var worker:Thread?=null
 private var trackName="Unknown track"
 private var playing=true
 private var writer:WavWriter?=null
 private val lock=Any()
 private val receiver=object:BroadcastReceiver() {
  override fun onReceive(context:Context?,intent:Intent?) {
   val title=intent?.getStringExtra("title").orEmpty()
   val artist=intent?.getStringExtra("artist").orEmpty()
   val next=if(title.isBlank()) "Unknown track" else "$artist - $title"
   synchronized(lock) {
    if(next!=trackName) { trackName=next;writer?.close();writer=null }
    playing=intent?.getBooleanExtra("playing",false)?:false
   }
  }
 }
 override fun onCreate() {
  super.onCreate()
  val channel=NotificationChannel(CHANNEL,"Music capture",NotificationManager.IMPORTANCE_LOW)
  (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP) { stopCapture();stopSelf();return START_NOT_STICKY }
  if(intent?.action!=START || running) return START_NOT_STICKY
  val notification=Notification.Builder(this,CHANNEL).setContentTitle("Music Capture Test").setContentText("Dahili ses kaydı testi").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
  startForeground(42,notification)
  val resultCode=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED)
  @Suppress("DEPRECATION")
  val data=intent.getParcelableExtra<Intent>("resultData")
  if(data==null) { stopSelf();return START_NOT_STICKY }
  try {
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection=mgr.getMediaProjection(resultCode,data)
   val config=AudioPlaybackCaptureConfiguration.Builder(projection!!).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build()
   val min=AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT)
   val size=maxOf(min,44100*4)
   recorder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()).setAudioPlaybackCaptureConfig(config).setBufferSizeInBytes(size).build()
   if(recorder?.state!=AudioRecord.STATE_INITIALIZED) { stopCapture();stopSelf();return START_NOT_STICKY }
   running=true
   recorder!!.startRecording()
   worker=thread(name="CaptureWriter") {
    val bytes=ByteArray(8192)
    while(running) {
     val count=try { recorder?.read(bytes,0,bytes.size)?:-1 } catch (_:Exception) { -1 }
     if(count>0) synchronized(lock) {
      try {
       if(playing) { if(writer==null) writer=WavWriter(this,trackName);writer?.write(bytes,count) }
       else { writer?.close();writer=null }
      } catch (_:Exception) { running=false }
     } else if(count<0) break
    }
    synchronized(lock) { writer?.close();writer=null }
   }
  } catch (_:Exception) { stopCapture();stopSelf() }
  return START_NOT_STICKY
 }
 private fun stopCapture() {
  running=false
  try { recorder?.stop() } catch (_:Exception) {}
  if(Thread.currentThread()!=worker) worker?.join(1500)
  synchronized(lock) { writer?.close();writer=null }
  recorder?.release();recorder=null;projection?.stop();projection=null
  stopForeground(STOP_FOREGROUND_REMOVE)
 }
 override fun onDestroy() { stopCapture();super.onDestroy() }
 override fun onBind(intent:Intent?)=null
}
private class WavWriter(private val context:Context,name:String) {
 private val uri:android.net.Uri
 private val out:java.io.OutputStream
 private var length=0L
 init {
  val safe=name.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(90).ifBlank { "Unknown track" }
  val date=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())
  val values=ContentValues().apply {
   put(MediaStore.Audio.Media.DISPLAY_NAME,"${safe}_$date.wav")
   put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav")
   put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/MusicCaptureTest")
   put(MediaStore.Audio.Media.IS_PENDING,1)
  }
  uri=context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values)?:error("Cannot create audio")
  out=context.contentResolver.openOutputStream(uri,"w")?:error("Cannot open audio")
  out.write(ByteArray(44))
 }
 fun write(bytes:ByteArray,count:Int) { out.write(bytes,0,count);length+=count }
 fun close() {
  try {
   out.flush();out.close()
   val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
   header.put("RIFF".toByteArray());header.putInt((36+length).toInt());header.put("WAVEfmt ".toByteArray());header.putInt(16)
   header.putShort(1.toShort());header.putShort(2.toShort());header.putInt(44100);header.putInt(44100*4);header.putShort(4.toShort());header.putShort(16.toShort())
   header.put("data".toByteArray());header.putInt(length.toInt())
   context.contentResolver.openFileDescriptor(uri,"rw")?.use { fd ->
    java.io.FileOutputStream(fd.fileDescriptor).channel.use { channel->channel.position(0);channel.write(ByteBuffer.wrap(header.array())) }
   }
   context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING,0) },null,null)
  } catch (_:Exception) {}
 }
}