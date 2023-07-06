package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
	@Required @SerialName( "public-address" ) val publicAddress: String = "minecraft.example.com",
	@Required @SerialName( "date-time-format" ) val dateTimeFormat: String = "dd/MM/yyyy HH:mm:ss z",
	@Required @SerialName( "server-icon-file-name" ) val serverIconFileName: String = "server-icon.png",
	@Required val discord: Discord = Discord(),
	@Required val http: HTTP = HTTP(),
	@Required @SerialName( "third-party" ) val thirdParty: ThirdParty = ThirdParty()
)
