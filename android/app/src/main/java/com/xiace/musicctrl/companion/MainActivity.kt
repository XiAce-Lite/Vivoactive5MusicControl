package com.xiace.musicctrl.companion

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshUi = object : Runnable {
        override fun run() {
            val snap = StatusBus.snapshot()
            statusText.text = buildString {
                append(if (snap.sdkReady) "SDK: OK\n" else "SDK: 未準備\n")
                append(snap.deviceSummary)
            }
            logText.text = snap.lines.joinToString("\n")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startCompanionService()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            sendService(CompanionService.ACTION_STOP)
            stopService(Intent(this, CompanionService::class.java))
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            sendService(CompanionService.ACTION_REFRESH)
            StatusBus.log("再スキャン要求")
        }
        findViewById<Button>(R.id.btnOpenWatch).setOnClickListener {
            sendService(CompanionService.ACTION_OPEN_WATCH)
        }
        findViewById<Button>(R.id.btnTestPoweramp).setOnClickListener {
            sendService(CompanionService.ACTION_TEST_TOGGLE)
        }
        findViewById<Button>(R.id.btnPushState).setOnClickListener {
            sendService(CompanionService.ACTION_PUSH_STATE)
        }

        StatusBus.log("Watch app id: ${Protocol.WATCH_APP_ID}")
        StatusBus.log("手順: Garmin Connect起動 → 本アプリ（自動でサービス開始）→ 時計でMusicCtrl")
        // Open activity ⇒ start bridge without requiring the Start button
        startCompanionService()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshUi)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshUi)
        super.onPause()
    }

    private fun startCompanionService() {
        val i = Intent(this, CompanionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i)
        } else {
            startService(i)
        }
        StatusBus.log("サービス開始要求")
    }

    private fun sendService(action: String) {
        val i = Intent(this, CompanionService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i)
        } else {
            startService(i)
        }
    }
}
