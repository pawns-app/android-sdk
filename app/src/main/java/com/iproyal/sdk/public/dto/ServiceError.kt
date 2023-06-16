package com.iproyal.sdk.public.dto

/**
 * Exhaustive list of all available Internet sharing service error types
 * @param reason shows more information on what was the issue.
 */
public sealed class ServiceError(public open val reason: String?) {

    /**
     * Thrown when internet sharing service is not going to attempt to share.
     * Most commonly thrown when setup is incorrect or user does not meet certain
     * criteria.
     */
    public data class Critical(override val reason: String) : ServiceError(reason)

    /**
     * Thrown when internet sharing service encounters recoverable issue
     * Most commonly thrown when connection cannot be established.
     */
    public data class General(override val reason: String) : ServiceError(reason)


    /**
     * This type is reserved for all other issues that might cause
     * internet sharing service not to run correctly.
     */
    public data class Unknown(override val reason: String?) : ServiceError(reason)
}