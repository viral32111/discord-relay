package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HTTP(
	@Required @SerialName( "user-agent-prefix" ) val userAgentPrefix: String = "Minecraft Server (https://example.com; contact@example.com)",
	@Required @SerialName( "from-address" ) val fromAddress: String = "contact@example.com",
	@Required @SerialName( "timeout-seconds" ) val timeoutSeconds: Long = 5
)
