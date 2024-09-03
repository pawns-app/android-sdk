package com.pawns.ndk

import android.util.Log

object PawnsCore {

    var isNdkLoaded: Boolean = false

    external fun Initialize(rawDeviceID: String, rawDeviceName: String)
    external fun StartMainRoutine(rawAccessToken: String?, callback: Callback)
    external fun StopMainRoutine()

    interface Callback {
        fun onCallback(callback: String)
    }

    init {
        // Used to load the 'pawns_ndk' library on application startup.
        try {
            System.loadLibrary("pawns_ndk")
            isNdkLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(this::class.simpleName, e.message.orEmpty())
        }
    }
}
