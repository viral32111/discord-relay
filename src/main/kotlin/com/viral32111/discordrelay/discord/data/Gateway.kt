package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Gateway(
	@Required @SerialName( "url" ) val url: String
)

// https://discord.com/developers/docs/topics/gateway-events#payload-structure
@Serializable
data class GatewayEventPayload(
	@Required @SerialName( "op" ) val operationCode: Int,
	@Required @SerialName( "s" ) val sequenceNumber: Int?,
	@Required @SerialName( "d" ) val eventData: JsonElement?,
	@Required @SerialName( "t" ) val eventName: String?
)
