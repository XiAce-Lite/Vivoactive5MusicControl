package com.xiace.musicctrl.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Start companion after device boot / unlock. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            CompanionStarter.ensureRunning(context, action)
        }
    }
}
