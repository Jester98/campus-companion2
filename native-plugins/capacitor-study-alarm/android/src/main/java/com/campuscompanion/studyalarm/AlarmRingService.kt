package com.campuscompanion.studyalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AlarmRingService : Service() {

  companion object {
    const val CHANNEL_ID = "study-timer-real-alarm"
    const val NOTIF_ID = 950001
    const val ACTION_STOP = "com.campuscompanion.studyalarm.ACTION_STOP"

    fun stop(context: Context) {
      context.stopService(Intent(context, AlarmRingService::class.java))
    }
  }

  private var mediaPlayer: MediaPlayer? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }

    val title = intent?.getStringExtra("title") ?: "Time's up!"
    val body = intent?.getStringExtra("body") ?: "Your focus session just ended."

    acquireWakeLock()
    startForeground(NOTIF_ID, buildNotification(title, body))
    playSound()
    vibrate()

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    mediaPlayer?.let {
      try { if (it.isPlaying) it.stop() } catch (e: Exception) {}
      it.release()
    }
    mediaPlayer = null
    (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(NOTIF_ID)
    super.onDestroy()
  }

  private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK, "StudyAlarm:RingWakeLock"
      ).apply {
      setReferenceCounted(false)
      acquire(10 * 60 * 1000L) // safety cap: never hold longer than 10 minutes
    }
  }

  private fun playSound() {
    try {
      val soundResId = resources.getIdentifier("study_alarm", "raw", packageName)
      if (soundResId == 0) return
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
          )
        val afd = resources.openRawResourceFd(soundResId)
        if (afd != null) {
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
        }
        isLooping = true
        prepare()
        start()
      }
    } catch (e: Exception) {
      // If playback fails for any reason, the notification/vibration still ring.
    }
  }

  private fun vibrate() {
    val pattern = longArrayOf(0, 800, 400, 800, 400)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
    } else {
      val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, 0)
      }
    }
  }

  private fun buildNotification(title: String, body: String): android.app.Notification {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val existing = nm.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID, "Study timer alarm", NotificationManager.IMPORTANCE_HIGH
          ).apply {
          description = "The real, looping alarm when a focus session ends"
          enableVibration(false)
          setSound(null, null)
        }
        nm.createChannel(channel)
      }
    }

    val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
      this, 0, fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val stopIntent = Intent(this, StopAlarmReceiver::class.java)
    val stopPendingIntent = PendingIntent.getBroadcast(
      this, 1, stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val smallIcon = resources.getIdentifier("ic_stat_study_alarm", "drawable", packageName)
    .let { if (it != 0) it else android.R.drawable.ic_lock_idle_alarm }

    return NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle(title)
    .setContentText(body)
    .setSmallIcon(smallIcon)
    .setPriority(NotificationCompat.PRIORITY_MAX)
    .setCategory(NotificationCompat.CATEGORY_ALARM)
    .setOngoing(true)
    .setAutoCancel(false)
    .setFullScreenIntent(fullScreenPendingIntent, true)
    .addAction(0, "Stop", stopPendingIntent)
    .setContentIntent(fullScreenPendingIntent)
    .build()
  }
}
