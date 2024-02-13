package com.iproyal.sdk.internal.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.iproyal.sdk.internal.logger.PawnsLogger
import com.iproyal.sdk.internal.service.PeerServiceBackground
import com.iproyal.sdk.internal.service.PeerServiceForeground
import com.iproyal.sdk.common.sdk.Pawns

internal object SystemUtils {

    internal fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        if (manager == null) {
            PawnsLogger.d(Pawns.TAG, "ActivityManager service is not available")
            return false
        }
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (PeerServiceForeground::class.java.name == service.service.className) {
                return true
            }
            if (PeerServiceBackground::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    internal fun getDeviceNameAndOsVersion(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val name = if (model.startsWith(manufacturer)) {
            model.replaceFirstChar { it.uppercase() }
        } else manufacturer.replaceFirstChar { it.uppercase() } + " " + model

        return name + " Android " + Build.VERSION.RELEASE
    }

}
