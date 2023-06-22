package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Embed
import com.viral32111.discordrelay.discord.data.Gateway
import com.viral32111.discordrelay.discord.data.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object API {
	private lateinit var apiBaseUrl: String

	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "application/json; */*"
	)

	private val JSON = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}

	// https://discord.com/developers/docs/resources/channel#allowed-mentions-object
	private val allowedMentions = buildJsonObject {
		put( "parse", buildJsonArray {} )
		put( "roles", buildJsonArray {} )
		put( "users", buildJsonArray {} )
		put( "replied_user", false )
	}

	fun initialize( configuration: Configuration ) {
		apiBaseUrl = "${ configuration.discord.api.baseUrl }/v${ configuration.discord.api.version }"
		DiscordRelay.LOGGER.info( "Discord API Base URL: '$apiBaseUrl'" )

		defaultHttpRequestHeaders[ "Authorization" ] = "Bot ${ configuration.discord.application.token }"
	}

	private suspend fun request( endpoint: String, method: String = "GET" ): JsonElement {
		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint" )
		DiscordRelay.LOGGER.info( "HTTP Response ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } byte(s))" )

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	private suspend fun request( endpoint: String, payload: JsonObject, method: String = "GET" ): JsonElement {
		val httpRequestHeaders = defaultHttpRequestHeaders.toMutableMap()
		httpRequestHeaders[ "Content-Type" ] = "application/json"

		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", httpRequestHeaders, JSON.encodeToString( payload ) )
		DiscordRelay.LOGGER.info( "HTTP Response ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } byte(s))" )

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	// https://discord.com/developers/docs/topics/gateway#get-gateway-bot
	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request(
		method = "GET",
		endpoint = "gateway/bot"
	) )

	// https://discord.com/developers/docs/resources/channel#create-message
	suspend fun createMessage( channelIdentifier: String, content: String? = null, embed: Embed? = null ): Message = JSON.decodeFromJsonElement( request(
		method = "POST",
		endpoint = "channels/$channelIdentifier/messages",
		payload = buildJsonObject {
			put( "allowed_mentions", allowedMentions )

			put( "content", content )

			if ( embed != null ) put( "embeds", buildJsonArray {
				add( JSON.encodeToJsonElement( embed ) )
			} )

			// TODO: components - https://discord.com/developers/docs/interactions/message-components#component-object

			// TODO: files, attachments & payload_json (uses multipart/form-data) - https://discord.com/developers/docs/reference#uploading-files
		}
	) )

	suspend fun createMessage( channelIdentifier: String, content: String ) = API.createMessage( channelIdentifier, content = content, embed = null )
	suspend fun createMessage( channelIdentifier: String, embed: Embed ) = API.createMessage( channelIdentifier, content = null, embed = embed )
}
