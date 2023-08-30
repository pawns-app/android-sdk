package com.iproyal.sdk.public.dto

public enum class ServiceType {
    /**
     * service that stays alive even when the app is terminated.
     */
    FOREGROUND,

    /**
     * service that runs only when the app is running, meaning it will get terminated
     * when the app is terminated.
     */
    BACKGROUND
}