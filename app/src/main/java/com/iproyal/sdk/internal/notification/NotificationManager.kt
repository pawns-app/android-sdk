package com.iproyal.sdk.internal.notification

import android.app.*
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.iproyal.sdk.public.sdk.Pawns


internal class NotificationManager constructor(
    private val context: Context
) {

    companion object {
        const val SERVICE_CHANNEL_ID = "CHANNEL_ID_13371351"
        const val CHANNEL_SERVICE_MESSAGE_ID = 13371351
    }

    private val serviceChannelName = "Sharing service"
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val serviceNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)

    init {
        initNotificationChannel()
    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = try {
                val metaData: Bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getApplicationInfo(
                        context.packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                }.metaData
                metaData.getString("com.iproyal.sdk.pawns_service_channel_name")
            } catch (e: Exception) {
                serviceChannelName
            }

            val importance = NotificationManager.IMPORTANCE_HIGH
            val serviceChannel = NotificationChannel(SERVICE_CHANNEL_ID, name, importance)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun createServiceNotification(): Notification {
        val serviceConfig = Pawns.instance.serviceConfig

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, CHANNEL_SERVICE_MESSAGE_ID,
            launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        serviceNotificationBuilder
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)

        serviceConfig.title?.let { serviceNotificationBuilder.setContentTitle(context.getString(it)) }
        serviceConfig.body?.let { serviceNotificationBuilder.setContentText(context.getString(it)) }
        serviceConfig.smallIcon?.let { serviceNotificationBuilder.setSmallIcon(it) }

        return serviceNotificationBuilder.build()
    }

}