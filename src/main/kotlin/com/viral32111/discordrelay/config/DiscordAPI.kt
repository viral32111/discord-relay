package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordAPI(
	@Required @SerialName( "base-url" ) val baseUrl: String = "https://discord.com/api",
	@Required @SerialName( "version" ) val version: Int = 10
)
