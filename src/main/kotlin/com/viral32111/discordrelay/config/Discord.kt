package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class Discord(
	@Required val application: DiscordApplication = DiscordApplication(),
	@Required val api: DiscordAPI = DiscordAPI(),
	@Required val gateway: DiscordGateway = DiscordGateway(),
	@Required val channels: DiscordChannels = DiscordChannels(),
	@Required val server: DiscordServer = DiscordServer(),
)
