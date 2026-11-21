package com.example.musiccapture
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MediaListener : NotificationListenerService() {
 private var manager:MediaSessionManager?=null
 private val callbacks=mutableMapOf<MediaController,MediaController.Callback>()
 private val listener=MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
 override fun onListenerConnected() { super.onListenerConnected();manager=getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager;manager?.addOnActiveSessionsChangedListener(listener,ComponentName(this,MediaListener::class.java));refresh() }
 private fun refresh() {
  val sessions=try { manager?.getActiveSessions(ComponentName(this,MediaListener::class.java)).orEmpty() } catch (_:SecurityException) { emptyList() }
  callbacks.keys.filter { it !in sessions }.forEach { c->callbacks.remove(c)?.let { c.unregisterCallback(it) } }
  sessions.filter { it.packageName=="com.spotify.music" || it.packageName=="com.google.android.apps.youtube.music" }.forEach { c->
   if(!callbacks.containsKey(c)) { val cb=object:MediaController.Callback() {
    override fun onMetadataChanged(metadata:MediaMetadata?) { publish(c) }
    override fun onPlaybackStateChanged(state:PlaybackState?) { publish(c) }
   };c.registerCallback(cb);callbacks[c]=cb }
   publish(c)
  }
 }
 private fun publish(c:MediaController) {
  val title=c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
  val artist=c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
  val playing=c.playbackState?.state==PlaybackState.STATE_PLAYING
  sendBroadcast(Intent(CaptureService.TRACK).setPackage(packageName).putExtra("title",title).putExtra("artist",artist).putExtra("playing",playing))
 }
 override fun onDestroy() { manager?.removeOnActiveSessionsChangedListener(listener);callbacks.forEach { (c,cb)->c.unregisterCallback(cb) };callbacks.clear();super.onDestroy() }
}