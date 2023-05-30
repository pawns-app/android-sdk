package com.iproyal.sdk.internal.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class SdkParameters(
    val error: String? = null,
    val message: String? = null,
)