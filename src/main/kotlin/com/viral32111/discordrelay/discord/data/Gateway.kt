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

		object Name {
			const val Ready = "READY"
			const val MessageCreate = "MESSAGE_CREATE"
			const val GuildCreate = "GUILD_CREATE"
		}

		// https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-opcodes
		object OperationCode {
			const val Dispatch = 0
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

				// https://discord.com/developers/docs/topics/gateway#gateway-intents
				object Intents {
					const val Guilds = 1 shl 0
					const val GuildMessages = 1 shl 9
					const val MessageContent = 1 shl 15
				}

			}

			// https://discord.com/developers/docs/topics/gateway-events#ready
			@Serializable
			data class Ready(
				@Required @SerialName( "v" ) val apiVersion: Int,
				@Required val user: User,
				@Required val guilds: List<Guild>,
				@Required @SerialName( "session_id" ) val sessionIdentifier: String,
				@Required @SerialName( "resume_gateway_url" ) val resumeUrl: String
			)

			// https://discord.com/developers/docs/topics/gateway-events#message-create
			// https://discord.com/developers/docs/resources/channel#message-object
			@Serializable
			data class MessageCreate(
				@Required @SerialName( "id" ) val identifier: String,
				@Required val type: Int,
				@Required val content: String,
				val member: Guild.Member? = null,
				@Required val author: User,
				@Required @SerialName( "channel_id" ) val channelIdentifier: String
			)

			// https://discord.com/developers/docs/topics/gateway-events#ready
			@Serializable
			data class Resume(
				@Required @SerialName( "token" ) val applicationToken: String,
				@Required @SerialName( "session_id" ) val sessionIdentifier: String,
				@Required @SerialName( "seq" ) val sequenceNumber: Int
			)

			// https://discord.com/developers/docs/topics/gateway-events#guild-create
			// https://discord.com/developers/docs/resources/guild#guild-object
			@Serializable
			data class GuildCreate(
				@Required @SerialName( "id" ) val identifier: String,
				@Required val name: String,
				@Required val roles: List<Guild.Role>
			)

		}

	}

}
