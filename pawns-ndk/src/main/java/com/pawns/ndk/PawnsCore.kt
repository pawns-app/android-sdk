package com.pawns.ndk

object PawnsCore {

    external fun Initialize(rawDeviceID: String, rawDeviceName: String)
    external fun StartMainRoutine(rawAccessToken: String?, callback: Callback)
    external fun StopMainRoutine()

    interface Callback {
        fun onCallback(callback: String)
    }

    init {
        // Used to load the 'pawns_ndk' library on application startup.
        System.loadLibrary("pawns_ndk")
    }
}
