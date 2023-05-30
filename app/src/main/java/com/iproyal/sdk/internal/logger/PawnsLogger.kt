package com.iproyal.sdk.internal.logger

import android.util.Log

internal object PawnsLogger {

    internal var isEnabled: Boolean = false

    internal fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (!isEnabled) return
        Log.e(tag, msg, tr)
    }

    internal fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (!isEnabled) return
        Log.d(tag, msg, tr)
    }

}