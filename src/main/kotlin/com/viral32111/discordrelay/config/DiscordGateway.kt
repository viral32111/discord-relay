package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordGateway(
	@Required @SerialName( "heartbeat-timeout-seconds" ) val heartbeatTimeoutSeconds: Long = 5
)
