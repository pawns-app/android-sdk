package com.pawns.sdk.internal.notification

import android.app.ActivityOptions
import android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pawns.sdk.common.dto.ServiceConfig
import com.pawns.sdk.common.dto.ServiceNotificationPriority


internal class NotificationManager constructor(
    private val context: Context,
    private val serviceConfig: ServiceConfig,
) {

    companion object {
        const val SERVICE_CHANNEL_ID = "CHANNEL_ID_13371351"
        const val CHANNEL_SERVICE_MESSAGE_ID = 13371351
    }

    private val serviceChannelName = "Sharing service"
    private val notificationManager: NotificationManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(NotificationManager::class.java)
    } else {
        ContextCompat.getSystemService(context, NotificationManager::class.java)
    }
    private val serviceNotificationBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)

    private val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
    } else {
        null
    }

    // Provided by consumer, who wishes to combine existing foreground service notification with ours
    private var externalNotification: Notification? = null
    private var externalNotificationId: Int? = null

    internal fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = try {
                val metaData: Bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getApplicationInfo(
                        context.packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    context.packageManager.getApplicationInfo(
                        context.packageName,
                        PackageManager.GET_META_DATA
                    )
                }.metaData
                metaData.getString("com.pawns.sdk.pawns_service_channel_name", serviceChannelName)
            } catch (e: Exception) {
                serviceChannelName
            }

            val importance = when (serviceConfig.notificationPriority) {
                ServiceNotificationPriority.LOW -> NotificationManager.IMPORTANCE_LOW
                ServiceNotificationPriority.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
                ServiceNotificationPriority.HIGH -> NotificationManager.IMPORTANCE_HIGH
            }
            val serviceChannel = NotificationChannel(SERVICE_CHANNEL_ID, name, importance)
            notificationManager?.createNotificationChannel(serviceChannel)
        }
    }

    internal fun createServiceNotification(): Notification {
        val currentExternalNotification = externalNotification
        if (currentExternalNotification != null) return currentExternalNotification

        val launchIntent = serviceConfig.launcherIntent ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context, CHANNEL_SERVICE_MESSAGE_ID,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                options
            )
        }

        val importance = when (serviceConfig.notificationPriority) {
            ServiceNotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            ServiceNotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            ServiceNotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        }

        serviceNotificationBuilder
            .setOnlyAlertOnce(true)
            .setPriority(importance)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setChannelId(SERVICE_CHANNEL_ID)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        serviceConfig.title?.let { serviceNotificationBuilder.setContentTitle(context.getString(it)) }
        serviceConfig.body?.let { serviceNotificationBuilder.setContentText(context.getString(it)) }
        serviceConfig.smallIcon?.let { serviceNotificationBuilder.setSmallIcon(it) }

        return serviceNotificationBuilder.build()
    }

    internal fun setExternalNotification(notification: Notification, notificationId: Int) {
        externalNotification = notification
        externalNotificationId = notificationId
    }

    internal fun getNotificationId(): Int {
        return externalNotificationId ?: CHANNEL_SERVICE_MESSAGE_ID
    }

}