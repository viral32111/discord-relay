package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/guild#guild-object
@Serializable
data class Guild(
	@Required @SerialName( "id" ) val identifier: String
) {

	// https://discord.com/developers/docs/resources/guild#guild-member-object
	@Serializable
	data class Member(
		val user: User? = null,
		@SerialName( "nick" ) val displayName: String? = null
	)

}
