package com.iproyal.sdk.internal.provider

import android.content.Context
import com.iproyal.sdk.internal.notification.NotificationManager
import com.iproyal.sdk.common.dto.ServiceConfig
import kotlinx.serialization.json.Json

internal class DependencyProvider(context: Context, serviceConfig: ServiceConfig) {

    internal val jsonInstance: Json by lazy { provideJson() }
    internal val notificationManager: NotificationManager by lazy { NotificationManager(context, serviceConfig) }

    private fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }



}