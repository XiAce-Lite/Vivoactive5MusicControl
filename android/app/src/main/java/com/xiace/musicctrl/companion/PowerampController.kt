package com.xiace.musicctrl.companion

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

/**
 * Controls PowerAMP via Intent API.
 * Commands must be broadcast to PowerampAPIReceiver (build 855+); startService is deprecated/unreliable.
 */
object PowerampController {
    private const val TAG = "PowerampController"
    private const val PKG = "com.maxmpz.audioplayer"
    private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    private const val API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    private const val EXTRA_COMMAND = "cmd"

    private const val CMD_TOGGLE_PLAY_PAUSE = 1
    private const val CMD_NEXT = 4
    private const val CMD_PREVIOUS = 5
    private const val CMD_POS_SYNC = 16

    fun toggle(context: Context) = sendCommand(context, CMD_TOGGLE_PLAY_PAUSE)
    fun next(context: Context) = sendCommand(context, CMD_NEXT)
    fun previous(context: Context) = sendCommand(context, CMD_PREVIOUS)
    fun requestPosSync(context: Context) = sendCommand(context, CMD_POS_SYNC)

    fun setVolumePercent(context: Context, percent: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val clamped = percent.coerceIn(0, 100)
        val index = ((clamped / 100.0) * max).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    fun getVolumePercent(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((cur * 100.0) / max).toInt().coerceIn(0, 100)
    }

    private fun sendCommand(context: Context, cmd: Int) {
        try {
            val intent = Intent(ACTION_API_COMMAND).apply {
                setClassName(PKG, API_RECEIVER)
                putExtra(EXTRA_COMMAND, cmd)
                putExtra("package", context.packageName)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "sent cmd=$cmd via broadcast receiver")
        } catch (e: Exception) {
            Log.e(TAG, "failed cmd=$cmd", e)
            StatusBus.log("PowerAMPコマンド失敗 cmd=$cmd: ${e.message}")
        }
    }
}
