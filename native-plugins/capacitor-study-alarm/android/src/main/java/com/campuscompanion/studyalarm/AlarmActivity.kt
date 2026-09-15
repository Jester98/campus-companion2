package com.campuscompanion.studyalarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class AlarmActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setShowWhenLockedAndTurnScreenOn()
    setContentView(buildLayout())
  }

  private fun setShowWhenLockedAndTurnScreenOn() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
      keyguardManager.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
  }

  // Built in code rather than a layout XML resource, so this plugin has no
  // dependency on the host app's resource/theme setup at all.
  private fun buildLayout(): LinearLayout {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(48, 48, 48, 48)
      setBackgroundColor(0xFF16203A.toInt())
    }
    val title = TextView(this).apply {
      text = "Time's up!"
      textSize = 28f
      setTextColor(0xFFFFFFFF.toInt())
      gravity = Gravity.CENTER
    }
    val subtitle = TextView(this).apply {
      text = "Your focus session just ended."
      textSize = 15f
      setTextColor(0xFFCCCCCC.toInt())
      gravity = Gravity.CENTER
      setPadding(0, 16, 0, 48)
    }
    val stopBtn = Button(this).apply {
      text = "Stop"
      textSize = 18f
      setOnClickListener {
        AlarmRingService.stop(this@AlarmActivity)
        finish()
      }
    }
    root.addView(title)
    root.addView(subtitle)
    root.addView(stopBtn)
    return root
  }
}
