package com.iproyal.sdk.internal.provider

import android.content.Context
import com.iproyal.sdk.internal.notification.NotificationManager
import kotlinx.serialization.json.Json

internal class DependencyProvider(private val context: Context) {

    internal val jsonInstance: Json by lazy { provideJson() }
    internal val notificationManager: NotificationManager = NotificationManager(context)

    private fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }



}