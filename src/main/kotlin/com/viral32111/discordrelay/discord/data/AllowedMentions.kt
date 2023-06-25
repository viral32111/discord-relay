package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/channel#allowed-mentions-object

@Serializable
data class AllowedMentions(
	val parse: List<String> = emptyList(),
	val roles: List<String> = emptyList(),
	val users: List<String> = emptyList(),
	@SerialName( "replied_user" ) val repliedUser: Boolean = false
)
