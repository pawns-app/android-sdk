package com.iproyal.sdk.public.dto

/**
 * Exhaustive list of all available Internet sharing service error types
 */
public sealed class ServiceError {

    /**
     * Thrown when internet sharing service was not able to authenticate user
     * Most commonly thrown when PawnsSdk is not initialised correctly or
     * Api key provided is not correct or active
     */
    public object Unauthorised : ServiceError()

    /**
     * This type is reserved for all other issues that might cause
     * internet sharing service not to run correctly
     * @param reason shows more information on what was the issue
     */
    public data class Unknown(val reason: String?) : ServiceError()
}