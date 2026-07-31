package com.xiace.musicctrl.companion

import java.util.concurrent.CopyOnWriteArrayList

/** Shared status lines for the MainActivity log. */
object StatusBus {
    data class Snapshot(
        val lines: List<String> = emptyList(),
        val sdkReady: Boolean = false,
        val deviceSummary: String = "デバイス未検出"
    )

    private val lines = CopyOnWriteArrayList<String>()
    @Volatile var sdkReady: Boolean = false
    @Volatile var deviceSummary: String = "デバイス未検出"

    fun log(msg: String) {
        val line = "${System.currentTimeMillis() % 100000}: $msg"
        lines.add(0, line)
        while (lines.size > 80) {
            lines.removeAt(lines.lastIndex)
        }
        android.util.Log.i("MusicCtrl", msg)
    }

    fun snapshot(): Snapshot = Snapshot(lines.toList(), sdkReady, deviceSummary)
}
