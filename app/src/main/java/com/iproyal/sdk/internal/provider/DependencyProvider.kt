package com.iproyal.sdk.internal.provider

import android.content.Context
import com.iproyal.sdk.internal.notification.NotificationManager
import com.iproyal.sdk.public.dto.ServiceType
import kotlinx.serialization.json.Json

internal class DependencyProvider(context: Context, serviceType: ServiceType) {

    internal val jsonInstance: Json by lazy { provideJson() }
    internal val notificationManager: NotificationManager = NotificationManager(context, serviceType)

    private fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }



}