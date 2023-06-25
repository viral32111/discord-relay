package com.viral32111.discordrelay.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscordAPI(
	@SerialName( "base-url" ) val baseUrl: String = "https://discord.com/api",
	@SerialName( "version" ) val version: Int = 10
)
