package com.xiace.musicctrl.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import java.util.concurrent.CopyOnWriteArrayList

class CompanionService : Service() {

    private lateinit var connectIQ: ConnectIQ
    private var sdkReady = false
    private val devices = CopyOnWriteArrayList<IQDevice>()
    private val watchApp = IQApp(Protocol.WATCH_APP_ID)
    private val emptyApp = IQApp("")
    private val handler = Handler(Looper.getMainLooper())

    private var playing = false
    private var title = "—"
    private var artist = "—"
    private var positionSec = 0
    private var durationSec = 180
    private var lastTrackId: Long = -1
    private var metaRefreshGeneration = 0
    private var expectNewTrackUntilMs: Long = 0
    private var lastLiveMetaUptimeMs: Long = 0
    /** After track meta update, ignore STATUS title/artist for a while (stale overwrite). */
    private var metaGuardUntilMs: Long = 0
    private var metaGeneration: Int = 0
    private var metaPushGeneration: Int = 0
    private var lastMetaPushKey: String = ""
    private var lastMetaPushAtMs: Long = 0

    /** Local progress clock — only valid when posClockArmed. */
    private var posClockArmed = false
    private var posBaseSec = 0
    private var posBaseUptimeMs = 0L

    private var lastLoggedDeviceSummary = ""
    private var lastAppInfoCheckMs = 0L
    private var lastCmdKey = ""
    private var lastCmdAtMs = 0L
    private var powerampReceiverRegistered = false
    private var httpServer: LocalCommandServer? = null
    private var batteryCheckCount = 0
    private var lastPushedBattery = -1
    private var playCheckCount = 0
    private var playMismatchCount = 0
    private var lastTransportCmdAtMs = 0L
    private var reconcileGeneration = 0
    private lateinit var audioManager: AudioManager

    /** Headphone connect/disconnect: PowerAMP may auto-resume/pause before we hear about it. */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            if (added.any { isHeadphone(it) }) scheduleReconcileBurst("イヤホン接続")
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { isHeadphone(it) }) scheduleReconcileBurst("イヤホン切断")
        }
    }

    /**
     * Dynamic receiver required: TPOS_SYNC is non-sticky and Android 8+ often
     * does not deliver it to manifest-registered receivers.
     */
    private val powerampReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) applyPowerampStatus(intent)
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (playing) {
                // PowerAMP is the source of truth for progress. Poll for a fresh
                // position snapshot, and only forward actual position updates.
                PowerampController.requestPosSync(this@CompanionService)
            }
            playCheckCount += 1
            if (playCheckCount >= 5) {
                playCheckCount = 0
                reconcilePlayState("定期確認", immediate = false, forcePush = false)
            }
            batteryCheckCount += 1
            if (batteryCheckCount >= 30) {
                batteryCheckCount = 0
                val bat = phoneBatteryPercent()
                if (bat >= 0 && bat != lastPushedBattery) {
                    pushStateToWatch()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (sdkReady) refreshDevicesAndListeners()
            handler.postDelayed(this, 5000)
        }
    }

    private val sdkListener = object : ConnectIQ.ConnectIQListener {
        override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
            sdkReady = false
            StatusBus.sdkReady = false
            StatusBus.log("SDK初期化失敗: $errStatus（Garmin Connect を起動していますか？）")
            notifyStatus("SDK失敗: $errStatus")
        }

        override fun onSdkReady() {
            sdkReady = true
            StatusBus.sdkReady = true
            StatusBus.log("Connect IQ SDK 準備完了")
            refreshDevicesAndListeners()
            openWatchAppOnDevices()
            pushStateToWatch()
            notifyStatus(StatusBus.deviceSummary)
        }

        override fun onSdkShutDown() {
            sdkReady = false
            StatusBus.sdkReady = false
            StatusBus.log("SDK shutdown")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIF_ID, buildNotification(getString(R.string.status_running)))
        StatusBus.log("サービス開始")
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        registerPowerampReceiver()
        httpServer = LocalCommandServer { cmd, vol ->
            handler.post { runWatchCommand(cmd, vol, "http") }
        }.also { it.start() }
        connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)
        try {
            connectIQ.initialize(applicationContext, true, sdkListener)
        } catch (e: Exception) {
            StatusBus.log("initialize例外: ${e.message}")
        }
        handler.postDelayed(refreshRunnable, 3000)
        handler.postDelayed(progressTicker, 1000)
        handler.postDelayed({
            bootstrapFromSticky()
            PowerampController.requestPosSync(this)
            pushStateToWatch()
        }, 500)
        // Also fires once on registration with already-connected devices.
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        scheduleReconcileBurst("起動")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PUSH_STATE -> {
                refreshDevicesAndListeners()
                pushStateToWatch()
            }
            ACTION_TEST_TOGGLE -> PowerampController.toggle(this)
            ACTION_OPEN_WATCH -> openWatchAppOnDevices()
            ACTION_REFRESH -> refreshDevicesAndListeners()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(progressTicker)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        unregisterPowerampReceiver()
        httpServer?.stop()
        httpServer = null
        teardownConnectIq()
        instance = null
        StatusBus.log("サービス停止")
        super.onDestroy()
    }

    private fun registerPowerampReceiver() {
        if (powerampReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction("com.maxmpz.audioplayer.STATUS_CHANGED")
            addAction("com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT")
            addAction("com.maxmpz.audioplayer.TRACK_CHANGED")
            addAction("com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT")
            addAction("com.maxmpz.audioplayer.TPOS_SYNC")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // RECEIVER_EXPORTED = 0x2 (constant missing on compileSdk 32)
            registerReceiver(powerampReceiver, filter, 0x2)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerampReceiver, filter)
        }
        powerampReceiverRegistered = true
        StatusBus.log("PowerAMP動的受信登録 (TPOS含む)")
    }

    private fun unregisterPowerampReceiver() {
        if (!powerampReceiverRegistered) return
        try {
            unregisterReceiver(powerampReceiver)
        } catch (_: Exception) {
        }
        powerampReceiverRegistered = false
    }

    /** Read sticky track/status for title/play only — never sticky pos. */
    private fun bootstrapFromSticky() {
        try {
            @Suppress("DEPRECATION")
            val track = registerReceiver(null, IntentFilter("com.maxmpz.audioplayer.TRACK_CHANGED"))
            if (track != null) applyPowerampStatus(track, push = false, allowPosition = false)
            @Suppress("DEPRECATION")
            val status = registerReceiver(null, IntentFilter("com.maxmpz.audioplayer.STATUS_CHANGED"))
            if (status != null) applyPowerampStatus(status, push = false, allowPosition = false)
        } catch (e: Exception) {
            StatusBus.log("sticky取得失敗: ${e.message}")
        }
    }

    private fun teardownConnectIq() {
        try {
            devices.forEach { d ->
                try {
                    connectIQ.unregisterForApplicationEvents(d, watchApp)
                    connectIQ.unregisterForApplicationEvents(d, emptyApp)
                    connectIQ.unregisterForDeviceEvents(d)
                } catch (_: Exception) {
                }
            }
            connectIQ.shutdown(applicationContext)
        } catch (_: Exception) {
        }
    }

    private fun refreshDevicesAndListeners() {
        try {
            val known = connectIQ.knownDevices ?: emptyList()
            devices.clear()
            val summaries = mutableListOf<String>()
            known.forEach { device ->
                val status = try {
                    connectIQ.getDeviceStatus(device)
                } catch (_: Exception) {
                    device.status
                }
                summaries.add("${device.friendlyName}:$status")
                devices.add(device)
                try {
                    connectIQ.registerForDeviceEvents(device) { d, st ->
                        StatusBus.log("デバイス状態 ${d.friendlyName} -> $st")
                        updateDeviceSummary()
                    }
                    connectIQ.registerForAppEvents(device, watchApp) { _, _, message, st ->
                        if (st != ConnectIQ.IQMessageStatus.SUCCESS) {
                            StatusBus.log("時計→携帯 status=$st")
                        }
                        handleWatchMessage(message)
                    }
                    connectIQ.registerForAppEvents(device, emptyApp) { _, _, message, _ ->
                        handleWatchMessage(message)
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - lastAppInfoCheckMs > 60_000L) {
                        lastAppInfoCheckMs = now
                        connectIQ.getApplicationInfo(Protocol.WATCH_APP_ID, device,
                            object : ConnectIQ.IQApplicationInfoListener {
                                override fun onApplicationInfoReceived(app: IQApp) {
                                    StatusBus.log("時計アプリ検出: status=${app.status}")
                                }

                                override fun onApplicationNotInstalled(applicationId: String?) {
                                    StatusBus.log("警告: 時計に MusicCtrl が見つからない")
                                }
                            })
                    }
                } catch (e: InvalidStateException) {
                    StatusBus.log("register失敗: ${e.message}")
                } catch (e: ServiceUnavailableException) {
                    StatusBus.log("ConnectIQサービス不可: Garmin Connect を開いてください")
                }
            }
            updateDeviceSummary(summaries)
        } catch (e: Exception) {
            StatusBus.log("デバイス取得失敗: ${e.message}")
            StatusBus.deviceSummary = "デバイス取得失敗"
        }
    }

    private fun updateDeviceSummary(list: List<String>? = null) {
        val text = when {
            list != null && list.isNotEmpty() -> list.joinToString(", ")
            devices.isEmpty() -> "デバイス0件（Garmin Connect で時計接続を確認）"
            else -> devices.joinToString(", ") { it.friendlyName ?: "?" }
        }
        StatusBus.deviceSummary = text
        if (text != lastLoggedDeviceSummary) {
            lastLoggedDeviceSummary = text
            StatusBus.log("接続: $text")
        }
        notifyStatus(text)
    }

    private fun openWatchAppOnDevices() {
        devices.forEach { device ->
            try {
                connectIQ.openApplication(device, watchApp) { _, _, status ->
                    StatusBus.log("openApplication -> $status")
                }
            } catch (e: Exception) {
                StatusBus.log("openApplication失敗: ${e.message}")
            }
        }
    }

    private fun handleWatchMessage(message: Any?) {
        when (message) {
            is Map<*, *> -> {
                val cmd = message[Protocol.KEY_CMD]?.toString()
                    ?: message["cmd"]?.toString()
                    ?: return
                val v = (message[Protocol.KEY_VOL] as? Number)?.toInt()
                    ?: message[Protocol.KEY_VOL]?.toString()?.toIntOrNull()
                runWatchCommand(cmd, v, "ble")
            }
            is String -> runWatchCommand(message, null, "ble")
            is List<*> -> {
                val map = HashMap<String, Any?>()
                message.forEach { item ->
                    if (item is Map<*, *> && item.containsKey("key")) {
                        map[item["key"].toString()] = item["value"]
                    }
                }
                if (map.isNotEmpty()) handleWatchMessage(map)
            }
            else -> { }
        }
    }

    private fun runWatchCommand(cmd: String, vol: Int?, source: String) {
        if (!acceptCommand(if (cmd == Protocol.CMD_VOL) "vol:$vol" else cmd)) return
        StatusBus.log("コマンド($source): $cmd" + if (vol != null) " v=$vol" else "")
        if (cmd == Protocol.CMD_TOGGLE || cmd == Protocol.CMD_NEXT || cmd == Protocol.CMD_PREV) {
            lastTransportCmdAtMs = SystemClock.uptimeMillis()
        }
        when (cmd) {
            Protocol.CMD_TOGGLE -> {
                if (playing) armClock(currentPositionSec())
                playing = !playing
                if (playing) armClock(positionSec) else posClockArmed = false
                PowerampController.toggle(this)
                pushStateToWatch()
                handler.postDelayed({
                    bootstrapFromSticky()
                    PowerampController.requestPosSync(this)
                    pushMetaToWatch("toggle")
                }, 400)
            }
            Protocol.CMD_NEXT -> skipTrack(true, "next")
            Protocol.CMD_PREV -> skipTrack(false, "prev")
            Protocol.CMD_VOL -> {
                if (vol != null) PowerampController.setVolumePercent(this, vol)
                handler.postDelayed({ pushStateToWatch() }, 150)
            }
            Protocol.CMD_SYNC -> reconcilePlayState("時計から同期要求", immediate = true, forcePush = true)
        }
    }

    private fun isHeadphone(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        TYPE_BLE_HEADSET -> true
        else -> false
    }

    /** PowerAMP may start/stop playback a few seconds after the trigger — check several times. */
    private fun scheduleReconcileBurst(reason: String) {
        StatusBus.log("再生状態の確認予約 ($reason)")
        val gen = ++reconcileGeneration
        listOf(1000L, 3000L, 6000L).forEach { delayMs ->
            handler.postDelayed({
                if (gen != reconcileGeneration) return@postDelayed
                reconcilePlayState(reason, immediate = true, forcePush = false)
            }, delayMs)
        }
    }

    /**
     * Re-read PowerAMP's last status, then cross-check with whether audio is actually
     * playing. Events alone miss auto-resume (e.g. headphones connected while the
     * service was not yet listening).
     */
    private fun reconcilePlayState(reason: String, immediate: Boolean, forcePush: Boolean) {
        val before = playing
        bootstrapFromSticky()
        val now = SystemClock.uptimeMillis()
        // Right after a watch command, PowerAMP/audio output can lag behind our own flag.
        if (now - lastTransportCmdAtMs >= 3000L) {
            val active = audioManager.isMusicActive
            if (active != playing) {
                playMismatchCount += 1
                // Periodic checks need two misses in a row to avoid flapping on brief silence.
                if (immediate || playMismatchCount >= 2) {
                    setPlayingState(active)
                    playMismatchCount = 0
                }
            } else {
                playMismatchCount = 0
            }
        }
        if (playing != before) {
            StatusBus.log("再生状態を補正 ($reason): ${if (playing) "再生中" else "停止中"}")
            if (playing) PowerampController.requestPosSync(this)
        }
        if (playing != before || forcePush) pushStateToWatchNow()
    }

    private fun setPlayingState(next: Boolean) {
        if (playing == next) return
        if (playing) armClock(currentPositionSec())
        playing = next
        if (playing) armClock(positionSec) else posClockArmed = false
    }

    private fun acceptCommand(key: String): Boolean {
        val now = SystemClock.uptimeMillis()
        if (key == lastCmdKey && now - lastCmdAtMs < 450L) return false
        lastCmdKey = key
        lastCmdAtMs = now
        return true
    }

    private fun skipTrack(next: Boolean, reason: String) {
        armClock(0)
        lastTrackId = -1
        expectNewTrackUntilMs = SystemClock.uptimeMillis() + 5000L
        if (next) PowerampController.next(this) else PowerampController.previous(this)
        pushStateToWatch()
        val gen = ++metaRefreshGeneration
        StatusBus.log("曲メタ再取得 ($reason)")
        listOf(250L, 800L).forEach { delay ->
            handler.postDelayed({
                if (gen != metaRefreshGeneration) return@postDelayed
                bootstrapFromSticky()
                if (SystemClock.uptimeMillis() < expectNewTrackUntilMs) armClock(0)
                PowerampController.requestPosSync(this)
                pushStateToWatch()
            }, delay)
        }
    }

    fun applyPowerampStatus(
        intent: Intent,
        push: Boolean = true,
        allowPosition: Boolean = true
    ) {
        val action = intent.action.orEmpty()
        val isTrackChanged = action.contains("TRACK_CHANGED")
        val isTpos = action.contains("TPOS_SYNC")
        val isStatus = action.contains("STATUS_CHANGED")
        val nowUp = SystemClock.uptimeMillis()

        // Debug: log every intent so we can see if PowerAMP is firing TRACK_CHANGED at all
        if (isTrackChanged || isTpos) {
            val dbgTitle = intent.getStringExtra("title")
                ?: intent.getBundleExtra(PowerampStatusReceiver.EXTRA_TRACK)?.getString("title")
                ?: "?"
            val dbgId = intent.getLongExtra("realId", intent.getLongExtra("id", -1L))
            val dbgPos = if (intent.hasExtra("pos")) intent.getIntExtra("pos", -1) else -1
            StatusBus.log("PA受信 $action title=$dbgTitle id=$dbgId pos=$dbgPos")
        }

        if (isTpos) {
            if (!allowPosition || !intent.hasExtra("pos")) return
            val newPos = parsePosSec(intent.getIntExtra("pos", -1))
            if (newPos < 0) return
            val before = currentPositionSec()
            if (nowUp < expectNewTrackUntilMs && before <= 2 && newPos > 5) return
            armClock(newPos)
            if (newPos != positionSec) {
                positionSec = newPos
            }
            val changed = newPos != before
            if (changed) {
                if (kotlin.math.abs(newPos - before) >= 2) {
                    StatusBus.log("TPOS/シーク: $before → $newPos")
                }
                if (push) pushStateToWatchNow()
            }
            return
        }

        val track = intent.getBundleExtra(PowerampStatusReceiver.EXTRA_TRACK)
        val trackId = readTrackRealId(intent, track)

        // During meta guard, STATUS must not overwrite title/artist with stale extras
        if (isStatus && nowUp < metaGuardUntilMs) {
            val wasPlaying = playing
            var nextPlaying = playing
            val state = intent.getIntExtra(PowerampStatusReceiver.EXTRA_STATE, -1)
            if (state == PowerampStatusReceiver.STATE_PLAYING) nextPlaying = true
            else if (state == PowerampStatusReceiver.STATE_PAUSED || state == PowerampStatusReceiver.STATE_STOPPED) nextPlaying = false
            if (intent.hasExtra("paused")) {
                nextPlaying = !intent.getBooleanExtra("paused", !nextPlaying)
            }
            if (wasPlaying != nextPlaying) {
                if (wasPlaying) armClock(currentPositionSec())
                playing = nextPlaying
                if (playing) armClock(positionSec) else posClockArmed = false
                if (push) pushStateToWatchNow()
            }
            return
        }

        val wasPlaying = playing
        var nextPlaying = playing
        val state = intent.getIntExtra(PowerampStatusReceiver.EXTRA_STATE, -1)
        if (state == PowerampStatusReceiver.STATE_PLAYING) nextPlaying = true
        else if (state == PowerampStatusReceiver.STATE_PAUSED || state == PowerampStatusReceiver.STATE_STOPPED) nextPlaying = false
        if (intent.hasExtra("paused")) {
            nextPlaying = !intent.getBooleanExtra("paused", !nextPlaying)
        }
        if (wasPlaying != nextPlaying) {
            if (wasPlaying) armClock(currentPositionSec())
            playing = nextPlaying
            if (playing) armClock(positionSec) else posClockArmed = false
            lastLiveMetaUptimeMs = nowUp
        } else {
            playing = nextPlaying
        }

        val newTitle = firstNonBlank(
            intent.getStringExtra("title"),
            track?.getString("title"),
            track?.getString(PowerampStatusReceiver.TRACK_TITLE)
        )
        val newArtist = firstNonBlank(
            intent.getStringExtra("artist"),
            track?.getString("artist"),
            track?.getString(PowerampStatusReceiver.TRACK_ARTIST)
        )

        val titleChanging = newTitle != null && newTitle != title
        val artistChanging = newArtist != null && newArtist != artist
        val idChanging = trackId > 0 && trackId != lastTrackId
        val expectingNew = nowUp < expectNewTrackUntilMs
        val trackBoundary = isTrackChanged || idChanging || titleChanging

        val durMs = intent.getIntExtra("durMs", track?.getInt("durMs", -1) ?: -1)
        if (durMs > 0) {
            durationSec = (durMs / 1000).coerceAtLeast(1)
        } else {
            val dur = intent.getIntExtra("dur", track?.getInt("dur", -1) ?: -1)
            if (dur > 0) durationSec = if (dur > 10_000) dur / 1000 else dur
        }

        // Any TRACK_CHANGED, or live meta change (auto-next sometimes only STATUS+title)
        val contentChanged = titleChanging || artistChanging || idChanging
        // isTrackChanged alone is not enough — PowerAMP often sends TRACK_CHANGED + _EXPLICIT
        val shouldAnnounce = contentChanged || (isTrackChanged && expectingNew)

        if (isTrackChanged) {
            StatusBus.log("TRACK_CHANGED判定: title変=$titleChanging artist変=$artistChanging id変=$idChanging expecting=$expectingNew → announce=$shouldAnnounce (cur=$title/$lastTrackId → new=${newTitle}/$trackId)")
        }

        // Natural auto-next can arrive as TRACK_CHANGED before fresh pos lands.
        // Reset the progress clock immediately so the watch does not keep waiting
        // for the previous song to reach its old end position.
        if (trackBoundary) {
            armClock(0)
        }

        if (newTitle != null && (isTrackChanged || titleChanging)) {
            title = newTitle
        }
        if (newArtist != null && (isTrackChanged || artistChanging)) {
            artist = newArtist
        }
        if (trackId > 0 && (isTrackChanged || idChanging || lastTrackId < 0)) {
            lastTrackId = trackId
        }

        if (shouldAnnounce) {
            expectNewTrackUntilMs = 0
            metaGuardUntilMs = nowUp + 4000L
            lastLiveMetaUptimeMs = nowUp
            StatusBus.log("曲情報: $artist / $title id=$lastTrackId pos=$positionSec/$durationSec play=$playing")
            if (push) {
                pushMetaToWatch(if (isTrackChanged) "TRACK_CHANGED" else "meta")
            }
            notifyStatus("$artist — $title")
            PowerampController.requestPosSync(this)
            return
        }

        // Duplicate TRACK_CHANGED_EXPLICIT with same meta — ignore, but keep the
        // just-reset progress and ask for a fresh position snapshot.
        if (isTrackChanged) {
            PowerampController.requestPosSync(this)
            if (push) pushStateToWatchNow()
            return
        }

        positionSec = currentPositionSec()
        if (push && wasPlaying != playing) {
            pushStateToWatchNow()
        }
    }

    /** Reliable meta delivery: retry a few times; dedupe identical meta within 2s. */
    private fun pushMetaToWatch(reason: String) {
        val key = "$lastTrackId|$title|$artist"
        val now = SystemClock.uptimeMillis()
        if (key == lastMetaPushKey && now - lastMetaPushAtMs < 2000L) {
            return
        }
        lastMetaPushKey = key
        lastMetaPushAtMs = now
        metaGeneration += 1
        val gen = ++metaPushGeneration
        StatusBus.log("時計へ曲送信 #$metaGeneration ($reason): $artist / $title")
        pushStateToWatchNow()
        listOf(250L, 700L, 1500L).forEach { delayMs ->
            handler.postDelayed({
                if (gen != metaPushGeneration) return@postDelayed
                pushStateToWatchNow()
            }, delayMs)
        }
    }

    private fun currentPositionSec(): Int {
        if (!playing || !posClockArmed) return positionSec.coerceAtLeast(0)
        val elapsed = ((SystemClock.uptimeMillis() - posBaseUptimeMs) / 1000L).toInt().coerceAtLeast(0)
        var est = posBaseSec + elapsed
        if (durationSec > 0 && est > durationSec) est = durationSec
        return est.coerceAtLeast(0)
    }

    private fun armClock(sec: Int) {
        val s = sec.coerceAtLeast(0)
        positionSec = s
        posBaseSec = s
        posBaseUptimeMs = SystemClock.uptimeMillis()
        posClockArmed = true
    }

    private fun parsePosSec(raw: Int): Int {
        if (raw < 0) return -1
        return if (raw > 10_000) raw / 1000 else raw
    }

    private fun readTrackRealId(intent: Intent, track: Bundle?): Long {
        fun fromBundle(b: Bundle, key: String): Long {
            if (!b.containsKey(key)) return -1L
            return when (val v = b.get(key)) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> -1L
            }
        }
        track?.let { fromBundle(it, "realId") }?.takeIf { it > 0 }?.let { return it }
        if (intent.hasExtra("realId")) {
            val n = when (val v = intent.extras?.get("realId")) {
                is Long -> v
                is Int -> v.toLong()
                is Number -> v.toLong()
                else -> -1L
            }
            if (n > 0) return n
        }
        return track?.let { fromBundle(it, "id") }?.takeIf { it > 0 } ?: -1L
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) if (!v.isNullOrBlank()) return v
        return null
    }

    /** Sticky battery broadcast. No extra permission. -1 if unavailable. */
    private fun phoneBatteryPercent(): Int {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale).coerceIn(0, 100)
    }

    private fun pushStateToWatch() = pushStateToWatchNow()

    private fun pushStateToWatchNow() {
        if (!sdkReady || devices.isEmpty()) {
            StatusBus.log("時計へ送信スキップ sdk=$sdkReady devices=${devices.size}")
            return
        }
        positionSec = currentPositionSec()
        val payload = HashMap<String, Any>()
        payload[Protocol.KEY_TYPE] = Protocol.KEY_STATE_TYPE
        payload[Protocol.KEY_PLAYING] = if (playing) 1 else 0
        payload[Protocol.KEY_TITLE] = title
        payload[Protocol.KEY_ARTIST] = artist
        payload[Protocol.KEY_POS] = positionSec
        payload[Protocol.KEY_DUR] = durationSec
        payload[Protocol.KEY_META_GEN] = metaGeneration
        payload[Protocol.KEY_VOL] = PowerampController.getVolumePercent(this)
        val battery = phoneBatteryPercent()
        if (battery >= 0) {
            lastPushedBattery = battery
            payload[Protocol.KEY_BATTERY] = battery
        }
        devices.forEach { device ->
            try {
                connectIQ.sendMessage(device, watchApp, payload) { _, _, status ->
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                        StatusBus.log("携帯→時計 送信: $status")
                    }
                }
            } catch (e: InvalidStateException) {
                StatusBus.log("送信InvalidState: ${e.message}")
            } catch (e: ServiceUnavailableException) {
                StatusBus.log("送信不可: Garmin Connect を起動")
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "MusicCtrl", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "musicctrl"
        private const val NOTIF_ID = 42
        /** AudioDeviceInfo.TYPE_BLE_HEADSET, added in API 31. */
        private const val TYPE_BLE_HEADSET = 26
        const val ACTION_STOP = "com.xiace.musicctrl.companion.STOP"
        const val ACTION_PUSH_STATE = "com.xiace.musicctrl.companion.PUSH_STATE"
        const val ACTION_TEST_TOGGLE = "com.xiace.musicctrl.companion.TEST_TOGGLE"
        const val ACTION_OPEN_WATCH = "com.xiace.musicctrl.companion.OPEN_WATCH"
        const val ACTION_REFRESH = "com.xiace.musicctrl.companion.REFRESH"

        @Volatile
        private var instance: CompanionService? = null

        fun isRunning(): Boolean = instance != null

        fun onPowerampIntent(context: Context, intent: Intent) {
            // Kept for compatibility; live handling is via dynamic receiver.
            instance?.applyPowerampStatus(intent)
        }
    }
}
