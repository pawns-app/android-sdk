package com.pawns.ndk

class NativeLib {

    external fun Initialize(rawDeviceID: String, rawDeviceName: String)
    external fun StartMainRoutine(rawAccessToken: String?, callback: Callback)
    external fun StopMainRoutine()

    interface Callback {
        fun onCallback(callback: String)
    }

    companion object {
        // Used to load the 'pawns_ndk' library on application startup.
        init {
            System.loadLibrary("pawns_ndk")
        }
    }
}