package com.iproyal.sdk.internal.provider

import android.content.Context
import com.iproyal.sdk.internal.notification.NotificationManager
import com.iproyal.sdk.common.dto.ServiceConfig
import com.iproyal.sdk.common.dto.ServiceType
import kotlinx.serialization.json.Json

internal class DependencyProvider(context: Context, serviceConfig: ServiceConfig, serviceType: ServiceType) {

    internal val jsonInstance: Json by lazy { provideJson() }
    internal val notificationManager: NotificationManager = NotificationManager(context, serviceConfig, serviceType)

    private fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }



}