package com.pawns.sdk.common.dto

import android.app.Notification

/**
 * Optional method. Allows to pass a your own notification that matches your already running foreground service notification.
 * This way Pawns SDK will not prompt user an additional notification when service is running.
 * @param notification - Notification object that must match your current foreground service notification (same channel)
 * @param notificationId - Same notification id that you passed to startForeground when launching your existing service
 */
public data class ServiceNotification(
   val notification: Notification,
   val notificationId: Int
)