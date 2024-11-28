package com.pawns.sdk.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SdkParameters(
    val error: String? = null,
    val message: String? = null,
    @SerialName("bytes_written")
    val traffic: String? = null,
)