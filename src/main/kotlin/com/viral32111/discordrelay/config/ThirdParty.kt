package com.viral32111.discordrelay.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThirdParty(
	@SerialName( "profile-url" ) val profileUrl: String = "https://namemc.com/profile/%s",
	@SerialName( "avatar-url" ) val avatarUrl: String = "https://crafatar.com/avatars/%s.png?size=128&overlay",
	@SerialName( "server-url" ) val serverUrl: String = "https://mcsrvstat.us/server/%s",
	@SerialName( "ip-address-url" ) val ipAddressUrl: String = "https://ipinfo.io/%s"
)
