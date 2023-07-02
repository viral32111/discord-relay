package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// https://discord.com/developers/docs/topics/gateway#get-gateway-bot
@Serializable
data class Gateway(
	@Required val url: String
) {

	// https://discord.com/developers/docs/topics/gateway-events#payload-structure
	@Serializable
	data class Event(
		@Required @SerialName( "op" ) val operationCode: Int,
		@SerialName( "s" ) val sequenceNumber: Int? = null,
		@SerialName( "t" ) val name: String? = null,
		@SerialName( "d" ) val data: JsonElement? = null
	) {

		// https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-opcodes
		object OperationCode {
			const val Ready = 0
			const val Heartbeat = 1
			const val Identify = 2
			const val Resume = 6
			const val Reconnect = 7
			const val InvalidSession = 9
			const val Hello = 10
			const val HeartbeatAcknowledgement = 11
		}

		object Data {

			// https://discord.com/developers/docs/topics/gateway-events#hello-hello-structure
			@Serializable
			data class Hello(
				@Required @SerialName( "heartbeat_interval" ) val heartbeatInterval: Long
			)

			// https://discord.com/developers/docs/topics/gateway-events#identify-identify-structure
			@Serializable
			data class Identify(
				@Required @SerialName( "token" ) val applicationToken: String,
				@Required @SerialName( "properties" ) val connectionProperties: ConnectionProperties,
				@Required val intents: Int
			) {

				// https://discord.com/developers/docs/topics/gateway-events#identify-identify-connection-properties
				@Serializable
				data class ConnectionProperties(
					@Required @SerialName( "os" ) val operatingSystemName: String,
					@Required @SerialName( "browser" ) val browserName: String,
					@Required @SerialName( "device" ) val deviceName: String,
				)

			}

		}

	}

}
