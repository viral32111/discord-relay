package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordCategoryChannel(
	@Required @SerialName( "id" ) val id: String = "",
	@Required @SerialName( "name" ) val name: String = "Minecraft (%s)"
)
