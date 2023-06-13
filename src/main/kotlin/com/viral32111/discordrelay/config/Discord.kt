package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Discord(
	@Required @SerialName( "application" ) val application: DiscordApplication = DiscordApplication(),
	@Required @SerialName( "api" ) val api: DiscordAPI = DiscordAPI(),
	@Required @SerialName( "channels" ) val channels: DiscordChannels = DiscordChannels()
)
