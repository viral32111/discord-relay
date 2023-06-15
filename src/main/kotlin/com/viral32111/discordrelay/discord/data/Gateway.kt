package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Gateway(
	@Required @SerialName( "url" ) val url: String,
	// Ignore shards & session_start_limit
)
