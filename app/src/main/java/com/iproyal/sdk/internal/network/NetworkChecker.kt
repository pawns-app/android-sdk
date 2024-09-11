package com.iproyal.sdk.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

internal class NetworkChecker {

    internal fun isVPNDetected(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                arrayOf(connectivityManager.activeNetwork)
            } else {
                connectivityManager.allNetworks
            }
            val hasVpnTransport = activeNetworks.any {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(it)
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            return hasVpnTransport
        } catch (e: Exception) {
            return false
        }
    }

}