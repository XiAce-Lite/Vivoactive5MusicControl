package com.xiace.musicctrl.companion

object Protocol {
    /** Must match Connect IQ manifest application id */
    const val WATCH_APP_ID = "a6c98919-2956-452a-a02b-592d01fbb039"

    const val KEY_CMD = "c"
    const val KEY_VOL = "v"
    const val KEY_TYPE = "t"
    const val KEY_PLAYING = "p"
    const val KEY_TITLE = "ti"
    const val KEY_ARTIST = "ar"
    const val KEY_POS = "pos"
    const val KEY_DUR = "dur"
    /** Increments on title/artist/track change so watch must apply meta. */
    const val KEY_META_GEN = "mg"
    const val KEY_STATE_TYPE = "st"

    const val CMD_TOGGLE = "toggle"
    const val CMD_NEXT = "next"
    const val CMD_PREV = "prev"
    const val CMD_VOL = "vol"
}
