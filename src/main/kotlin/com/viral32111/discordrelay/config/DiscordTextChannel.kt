package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordTextChannel(
	@Required @SerialName( "id" ) val identifier: String = "",
	@Required val webhook: DiscordWebhook = DiscordWebhook(),
)
