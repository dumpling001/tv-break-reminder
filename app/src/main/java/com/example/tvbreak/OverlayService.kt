package com.example.tvbreak

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ showOverlay() }, 60_000)
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "tvbreak"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, "TVBreak", NotificationManager.IMPORTANCE_LOW)
            )
            return Notification.Builder(this, channelId)
                .setContentTitle("TVBreak 运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        return Notification.Builder(this)
            .setContentTitle("TVBreak 运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF9800"))
            addView(TextView(context).apply {
                text = "🚶"
                textSize = 140f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "休息啦！去阳台走一圈"
                textSize = 48f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
        }
        wm.addView(layout, params)
        overlay = layout
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            .startTone(ToneGenerator.TONE_PROP_BEEP, 1500)
        handler.postDelayed({ removeOverlay(); stopSelf() }, 15_000)
    }

    private fun removeOverlay() {
        try {
            overlay?.let {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            }
        } catch (e: Exception) {
        }
        overlay = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }
}