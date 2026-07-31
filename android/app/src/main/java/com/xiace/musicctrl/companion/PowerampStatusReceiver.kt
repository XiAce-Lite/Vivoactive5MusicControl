package com.xiace.musicctrl.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest receiver: wake companion when PowerAMP fires.
 * Actual status/pos handling is done by CompanionService's dynamic receiver
 * (TPOS_SYNC is non-sticky and often not delivered to manifest receivers on Android 8+).
 */
class PowerampStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        Log.d(TAG, "wake action=${intent.action}")
        CompanionStarter.ensureRunning(context, intent.action ?: "poweramp")
    }

    companion object {
        private const val TAG = "PowerampStatusReceiver"

        const val EXTRA_STATE = "state"
        const val EXTRA_TRACK = "track"
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2

        const val TRACK_TITLE = "title"
        const val TRACK_ARTIST = "artist"
        const val TRACK_POS = "pos"
        const val TRACK_DURATION = "dur"
    }
}
