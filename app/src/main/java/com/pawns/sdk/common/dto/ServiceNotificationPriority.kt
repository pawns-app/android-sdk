package com.pawns.sdk.common.dto

/**
 * Corresponds to original notification importance settings
 * @see androidx.core.app.NotificationCompat Priority settings
 * @see android.app.NotificationManager Importance settings
 */
public enum class ServiceNotificationPriority {
    LOW,
    DEFAULT,
    HIGH
}