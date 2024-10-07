package com.pawns.sdk.common.dto

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Optional Configuration class.
 * Tailors the behavior of Internet sharing service.
 * @param title string resource for notification when service is running.
 * @param body string resource for notification when service is running.
 * @param smallIcon drawable resource for notification when service is running.
 * @param notificationPriority sets priority for service notification channel and notification itself
 * @param launcherIntent intent passed to pendingIntent of notification, default value is null that translates to application's launcher activity
 */
public data class ServiceConfig(
    @StringRes val title: Int? = null,
    @StringRes val body: Int? = null,
    @DrawableRes val smallIcon: Int? = null,
    val notificationPriority: ServiceNotificationPriority = ServiceNotificationPriority.DEFAULT,
    val launcherIntent: Intent? = null
)