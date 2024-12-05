package com.pawns.sdk.internal.dto

internal enum class SdkErrorType(val sdkValue: String) {
    NO_FREE_PORT("cant_get_free_port"),
    NON_RESIDENTIAL("non_residential_ip"),
    UNSUPPORTED("unsupported_version"),
    UNAUTHORISED("unauthorized"),
    LOST_CONNECTION("lost_connection"),
    IP_USED("ip_used"),
    PEER_ALIVE_FAILED("could_not_mark_peer_alive"),
    CANT_OPEN_PORT("cant_open_port")
}