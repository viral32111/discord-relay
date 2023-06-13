package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordChannels(
	@Required @SerialName( "relay" ) val relay: DiscordTextChannel = DiscordTextChannel(),
	@Required @SerialName( "log" ) val log: DiscordTextChannel = DiscordTextChannel(),
	@Required @SerialName( "status" ) val status: DiscordCategoryChannel = DiscordCategoryChannel()
)
