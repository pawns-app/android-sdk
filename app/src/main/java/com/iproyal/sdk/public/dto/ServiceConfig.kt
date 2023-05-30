package com.iproyal.sdk.public.dto

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Optional Configuration class.
 * Tailors the behavior of Internet sharing service.
 * @param title string resource for notification when service is running.
 * @param body string resource for notification when service is running.
 * @param smallIcon drawable resource for notification when service is running.
 */
public data class ServiceConfig(
    @StringRes val title: Int? = null,
    @StringRes val body: Int? = null,
    @DrawableRes val smallIcon: Int? = null,
)