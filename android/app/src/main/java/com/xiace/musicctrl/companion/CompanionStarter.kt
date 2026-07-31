package com.xiace.musicctrl.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/** Starts the foreground companion service; falls back to a tap-to-start notification. */
object CompanionStarter {
    private const val TAG = "CompanionStarter"
    private const val CHANNEL_ID = "musicctrl_prompt"
    private const val PROMPT_ID = 43

    fun ensureRunning(context: Context, reason: String) {
        val app = context.applicationContext
        if (CompanionService.isRunning()) {
            return
        }
        StatusBus.log("自動起動要求: $reason")
        val intent = Intent(app, CompanionService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FGS start blocked: ${e.message}")
            StatusBus.log("自動起動不可 → 通知で案内 (${e.javaClass.simpleName})")
            showStartPrompt(app)
        }
    }

    private fun showStartPrompt(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MusicCtrl起動", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val start = PendingIntent.getService(
            context, 1,
            Intent(context, CompanionService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("MusicCtrl Companion")
            .setContentText("タップしてブリッジを開始")
            .setContentIntent(open)
            .addAction(0, "開始", start)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(PROMPT_ID, n)
    }
}
