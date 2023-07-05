package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/user#user-object
@Serializable
data class User(
	@Required @SerialName( "id" ) val identifier: String,
	@Required @SerialName( "username" ) val name: String,
	@Required val discriminator: String, // TODO: This is being phased out
	@SerialName( "global_name" ) val displayName: String? = null,
	@SerialName( "avatar" ) val avatarHash: String? = null,
	@SerialName( "bot" ) val isBot: Boolean? = null,
	@SerialName( "system" ) val isSystem: Boolean? = null
)
