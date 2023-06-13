package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThirdParty(
	@Required @SerialName( "profile-url" ) val profileUrl: String = "https://namemc.com/profile/%s",
	@Required @SerialName( "avatar-url" ) val avatarUrl: String = "https://crafatar.com/avatars/%s.png?size=128&overlay",
)
