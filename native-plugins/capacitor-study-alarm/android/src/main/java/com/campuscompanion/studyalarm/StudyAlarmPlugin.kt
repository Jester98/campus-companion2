package com.campuscompanion.studyalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    // Android 12+ (API 31) requires the SCHEDULE_EXACT_ALARM permission to be
    // explicitly granted via system settings before an exact alarm can be
    // scheduled -- calling setAlarmClock() without it throws a
    // SecurityException that would otherwise crash the whole app. Reject
    // cleanly instead so the caller (JS) can fall back to a plain
    // notification.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
      call.reject("SCHEDULE_EXACT_ALARM permission not granted")
      return
    }

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

    try {
      val info = AlarmManager.AlarmClockInfo(atMillis, showPendingIntent)
      alarmManager.setAlarmClock(info, firePendingIntent)
    } catch (e: SecurityException) {
      call.reject("Unable to schedule exact alarm: ${e.message}")
      return
    }

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

  @PluginMethod
  fun checkExactAlarmPermission(call: PluginCall) {
    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      alarmManager.canScheduleExactAlarms()
    } else {
      true
    }
    val ret = JSObject()
    ret.put("granted", granted)
    call.resolve(ret)
  }

  @PluginMethod
  fun requestExactAlarmPermission(call: PluginCall) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val context = context
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      if (!alarmManager.canScheduleExactAlarms()) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
          data = Uri.parse("package:" + context.packageName)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      }
    }
    call.resolve()
  }
}
