package com.campuscompanion.studyalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra("title") ?: "Time's up!"
    val body = intent.getStringExtra("body") ?: "Your focus session just ended."

    val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
      putExtra("title", title)
      putExtra("body", body)
    }
    ContextCompat.startForegroundService(context, serviceIntent)
  }
}
