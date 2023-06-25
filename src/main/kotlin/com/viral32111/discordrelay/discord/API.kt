package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.*
import com.viral32111.discordrelay.discord.data.Gateway
import kotlinx.atomicfu.TraceBase.None.append
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object API {
	private lateinit var apiBaseUrl: String

	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "application/json; */*"
	)

	private val JSON = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}

	fun initialize( configuration: Configuration ) {
		apiBaseUrl = "${ configuration.discord.api.baseUrl }/v${ configuration.discord.api.version }"
		DiscordRelay.LOGGER.info( "Discord API Base URL: '$apiBaseUrl'" )

		defaultHttpRequestHeaders[ "Authorization" ] = "Bot ${ configuration.discord.application.token }"
	}

	private suspend fun request( endpoint: String, method: String = "GET" ): JsonElement {
		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", headers = defaultHttpRequestHeaders )
		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	private suspend fun request( endpoint: String, payload: JsonObject, method: String = "GET" ): JsonElement {
		val httpRequestHeaders = defaultHttpRequestHeaders.toMutableMap()
		httpRequestHeaders[ "Content-Type" ] = "application/json"

		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", httpRequestHeaders, JSON.encodeToString( payload ) )
		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	// https://discord.com/developers/docs/topics/gateway#get-gateway-bot
	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request(
		method = "GET",
		endpoint = "gateway/bot"
	) )

	// https://discord.com/developers/docs/resources/channel#create-message
	/* TODO: Fallback to messages via bot if webhook id & token are not set
	private suspend fun createMessage( channelIdentifier: String, content: String? = null, embed: Embed? = null, userName: String? = null, avatarUrl: String? = null ): Message = JSON.decodeFromJsonElement( request(
		method = "POST",
		endpoint = "channels/$channelIdentifier/messages",
		payload = buildJsonObject {
			put( "allowed_mentions", allowedMentions )

			put( "avatar_url", avatarUrl )
			put( "username", userName )

			put( "content", content )

			if ( embed != null ) put( "embeds", buildJsonArray {
				add( JSON.encodeToJsonElement( embed ) )
			} )

			// TODO: components - https://discord.com/developers/docs/interactions/message-components#component-object

			// TODO: files, attachments & payload_json (uses multipart/form-data) - https://discord.com/developers/docs/reference#uploading-files
		}
	) )

	suspend fun createMessage( channelIdentifier: String, avatarUrl: String?, userName: String?, content: String ) = createMessage( channelIdentifier, content = content, embed = null, avatarUrl = avatarUrl, userName = userName )
	suspend fun createMessage( channelIdentifier: String, embed: Embed ) = createMessage( channelIdentifier, content = null, embed = embed )
	*/

	// https://discord.com/developers/docs/resources/webhook#execute-webhook
	suspend fun sendWebhookText( identifier: String, token: String, builderBlock: WebhookMessageBuilder.() -> Unit ): Message = JSON.decodeFromJsonElement( request(
		method = "POST",
		endpoint = "webhooks/$identifier/$token?wait=true",
		payload = JSON.encodeToJsonElement( WebhookMessageBuilder().apply( builderBlock ).apply { preventMentions() }.build() ) as JsonObject
	) )
	suspend fun sendWebhookEmbed( identifier: String, token: String, builderBlock: EmbedBuilder.() -> Unit ): Message = JSON.decodeFromJsonElement( request(
		method = "POST",
		endpoint = "webhooks/$identifier/$token?wait=true",
		payload = JSON.encodeToJsonElement( createWebhookMessage {
			preventMentions()
			embeds = mutableListOf( EmbedBuilder().apply( builderBlock ).build() )
		} ) as JsonObject
	) )

	suspend fun sendWebhookAttachmentEmbed( identifier: String, token: String, filePath: Path, builderBlock: EmbedBuilder.() -> Unit ) {
		val boundary = "1e5354201eb19853"

		val headers = defaultHttpRequestHeaders.toMutableMap()
		headers[ "Content-Type" ] = "multipart/form-data; boundary=$boundary"

		val file = filePath.toFile()

		val multipartBody = StringBuilder().apply {
			append( "--$boundary\r\n" )
			append( "Content-Disposition: form-data; name=\"payload_json\"\r\n\r\n" )
			append( JSON.encodeToString( createWebhookMessage {
				embeds = listOf( EmbedBuilder().apply( builderBlock ).build() )
			} ) )
			append( "\r\n--$boundary\r\n" )
			append( "Content-Disposition: form-data; name=\"file\"; filename=\"${ file.name }\"\r\n" )
			append( "Content-Type: ${ Files.probeContentType( filePath ) }\r\n\r\n" )
			append( file.readBytes() )
			append( "\r\n--$boundary--" )
		}.toString()

		val httpResponse = HTTP.request(
			method = "POST",
			url = "$apiBaseUrl/webhooks/$identifier/$token?wait=true",
			headers = headers,
			body = multipartBody
		)

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}
}
