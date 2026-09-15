package com.campuscompanion.studyalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "StudyAlarm")
class StudyAlarmPlugin : Plugin() {

  companion object {
    const val ACTION_ALARM_FIRED = "com.campuscompanion.studyalarm.ACTION_ALARM_FIRED"
    const val REQUEST_CODE = 5501
    const val PREFS_NAME = "study_alarm_prefs"
    const val PREF_SCHEDULED_AT = "scheduled_at"
  }

  @PluginMethod
  fun schedule(call: PluginCall) {
    val atMillis = call.getLong("atMillis", 0L) ?: 0L
    if (atMillis <= System.currentTimeMillis()) {
      call.reject("atMillis must be in the future")
      return
    }
    val title = call.getString("title") ?: "Time's up!"
    val body = call.getString("body") ?: "Your focus session just ended."

    val context = context
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val showIntent = Intent(context, AlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val showPendingIntent = PendingIntent.getActivity(
      context, REQUEST_CODE, showIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_ALARM_FIRED
      putExtra("title", title)
      putExtra("body", body)
    }
    val firePendingIntent = PendingIntent.getBroadcast(
      context, REQUEST_CODE, fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val info = AlarmManager.AlarmClockInfo(atMillis, showPendingIntent)
    alarmManager.setAlarmClock(info, firePendingIntent)

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    .putLong(PREF_SCHEDULED_AT, atMillis).apply()

    val ret = JSObject()
    ret.put("scheduled", true)
    call.resolve(ret)
  }

  @PluginMethod
  fun cancel(call: PluginCall) {
    val context = context
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_ALARM_FIRED
    }
    val firePendingIntent = PendingIntent.getBroadcast(
      context, REQUEST_CODE, fireIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    alarmManager.cancel(firePendingIntent)

    AlarmRingService.stop(context)

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
    .remove(PREF_SCHEDULED_AT).apply()

    call.resolve()
  }

  @PluginMethod
  fun isScheduled(call: PluginCall) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val at = prefs.getLong(PREF_SCHEDULED_AT, -1L)
    val ret = JSObject()
    ret.put("scheduled", at > System.currentTimeMillis())
    if (at > 0) ret.put("atMillis", at)
    call.resolve(ret)
  }
}
