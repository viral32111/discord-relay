package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/guild#guild-object
@Serializable
data class Guild(
	@Required @SerialName( "id" ) val identifier: String,
	val unavailable: Boolean? = null
) {

	// https://discord.com/developers/docs/resources/guild#guild-member-object
	@Serializable
	data class Member(
		val user: User? = null,
		@SerialName( "nick" ) val displayName: String? = null,
		@Required @SerialName( "roles" ) val roleIdentifiers: List<String>,
	)

	// https://discord.com/developers/docs/topics/permissions#role-object
	@Serializable
	data class Role(
		@Required @SerialName( "id" ) val identifier: String,
		@Required val name: String,
		@Required val color: Int,
		@Required val position: Int
	)

	// https://discord.com/developers/docs/resources/channel#channel-object
	@Serializable
	data class Channel(
		@Required @SerialName( "id" ) val identifier: String,
		val name: String? = null
	)

}
