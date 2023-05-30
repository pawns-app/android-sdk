package com.iproyal.sdk.internal.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class SdkEvent(
    val name: String?,
    val parameters: SdkParameters?,
)

internal enum class SdkLifeCycleName(val sdkValue: String) {

    STARTING("starting"),
    NOT_RUNNING("not_running"),
    RUNNING("running"),
}

