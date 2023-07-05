package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/channel#message-object
@Serializable
data class Message(
	@Required @SerialName( "id" ) val identifier: String
)
