package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class DiscordChannels(
	@Required val relay: DiscordTextChannel = DiscordTextChannel(),
	@Required val log: DiscordTextChannel = DiscordTextChannel(),
	@Required val status: DiscordCategoryChannel = DiscordCategoryChannel()
)
