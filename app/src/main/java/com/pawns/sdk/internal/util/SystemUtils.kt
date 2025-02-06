package com.pawns.sdk.internal.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.pawns.sdk.common.sdk.Pawns
import com.pawns.sdk.internal.logger.PawnsLogger
import com.pawns.sdk.internal.service.PeerServiceBackground
import com.pawns.sdk.internal.service.PeerServiceForeground

internal object SystemUtils {

    internal fun isServiceRunning(
        context: Context,
        services: List<String> = listOf(PeerServiceForeground::class.java.name, PeerServiceBackground::class.java.name)
    ): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        if (manager == null) {
            PawnsLogger.d(Pawns.TAG, "ActivityManager service is not available")
            return false
        }
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            services.forEach { className ->
                if (className == service.service.className) {
                    return true
                }
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
