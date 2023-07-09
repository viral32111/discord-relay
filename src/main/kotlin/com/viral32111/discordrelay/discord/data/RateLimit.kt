package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/topics/rate-limits#exceeding-a-rate-limit-rate-limit-response-structure

@Serializable
data class RateLimit(
	@Required @SerialName( "retry_after" ) val retryAfter: Double,
	@Required @SerialName( "global" ) val isGlobal: Boolean
)
