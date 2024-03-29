package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.createFormData
import com.viral32111.discordrelay.discord.builder.ChannelBuilder
import com.viral32111.discordrelay.discord.data.*
import com.viral32111.discordrelay.discord.data.Gateway
import kotlinx.coroutines.time.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.jvm.optionals.getOrElse
import kotlin.math.roundToLong

object API {
	private lateinit var apiBaseUrl: String

	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "application/json; */*"
	)

	fun initialize( configuration: Configuration ) {
		apiBaseUrl = "${ configuration.discord.api.baseUrl }/v${ configuration.discord.api.version }"
		DiscordRelay.LOGGER.debug( "Discord API Base URL: '$apiBaseUrl'" )

		defaultHttpRequestHeaders[ "Authorization" ] = "Bot ${ configuration.discord.application.token }"
	}

	private suspend fun request( method: String, endpoint: String, payload: JsonObject? = null, retryDepth: Int = 0 ): JsonElement {
		if ( retryDepth > 0 ) DiscordRelay.LOGGER.debug( "Attempt #$retryDepth of retrying $method '$endpoint'..." )

		val httpRequestHeaders = defaultHttpRequestHeaders.toMutableMap() // Creates a copy
		if ( payload != null ) httpRequestHeaders[ "Content-Type" ] = "application/json; charset=utf-8"

		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", httpRequestHeaders, body = if ( payload != null ) JSON.encodeToString( payload ) else null )

		val httpResponseStatusCode = httpResponse.statusCode()
		val httpResponseHeaders = httpResponse.headers()

		val rateLimitRequestLimit = httpResponseHeaders.firstValue( "X-RateLimit-Limit" ).getOrElse { null }?.toLongOrNull()
		val rateLimitRemainingRequests = httpResponseHeaders.firstValue( "X-RateLimit-Remaining" ).getOrElse { null }?.toLongOrNull()
		val rateLimitResetTimestamp = httpResponseHeaders.firstValue( "X-RateLimit-Reset" ).getOrElse { null }?.toDoubleOrNull()
		val rateLimitResetAfterSeconds = httpResponseHeaders.firstValue( "X-RateLimit-Reset-After" ).getOrElse { null }?.toDoubleOrNull()
		val rateLimitBucketIdentifier = httpResponseHeaders.firstValue( "X-RateLimit-Bucket" ).getOrElse { null }
		DiscordRelay.LOGGER.debug( "$rateLimitRemainingRequests of $rateLimitRequestLimit request(s) remaining for $method '$endpoint' ($rateLimitBucketIdentifier), resets after $rateLimitResetAfterSeconds second(s) or at $rateLimitResetTimestamp." )

		if ( httpResponseStatusCode == 429 ) {
			val rateLimitIsGlobal = httpResponseHeaders.firstValue( "X-RateLimit-Global" ).getOrElse { null } == "1"
			val rateLimitScopeName = httpResponseHeaders.firstValue( "X-RateLimit-Scope" ).getOrElse { null }
			val rateLimit = JSON.decodeFromString<RateLimit>( httpResponse.body() )

			DiscordRelay.LOGGER.warn( "Hit ${ if ( rateLimitIsGlobal || rateLimit.isGlobal ) "global" else "route" } rate limit for $method '$endpoint' (Bucket: '$rateLimitBucketIdentifier', Scope: '$rateLimitScopeName') with $rateLimitRemainingRequests of $rateLimitRequestLimit remaining request(s)! Retrying in ${ rateLimit.retryAfter } second(s)..." )

			if ( retryDepth > 3 ) throw RuntimeException( "Attempted retry Discord API request $method '$endpoint' too many times" )

			// Wait & retry
			delay( Duration.ofSeconds( rateLimit.retryAfter.roundToLong() ) )
			return request( method, endpoint, payload, retryDepth + 1 )
		}

		if ( httpResponseStatusCode < 200 || httpResponseStatusCode >= 300 ) throw HTTP.HttpException( httpResponseStatusCode, httpResponse.request().method(), httpResponse.request().uri() )

		return if ( httpResponse.statusCode() == 204 ) JSON.encodeToJsonElement( "" ) else JSON.decodeFromString( httpResponse.body() )
	}

	// https://discord.com/developers/docs/topics/gateway#get-gateway-bot
	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request(
		method = HTTP.Method.Get,
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

	// https://discord.com/developers/docs/resources/channel#modify-channel
	suspend fun updateChannel( identifier: String, builderBlock: ChannelBuilder.() -> Unit ): Guild.Channel = JSON.decodeFromJsonElement( request(
		method = HTTP.Method.Patch,
		endpoint = "channels/$identifier",
		payload = JSON.encodeToJsonElement( ChannelBuilder().apply( builderBlock ).build() ) as JsonObject
	) )

	// https://discord.com/developers/docs/resources/webhook#execute-webhook
	private suspend fun sendWebhookText( identifier: String, token: String, shouldWait: Boolean, builderBlock: WebhookMessageBuilder.() -> Unit ): JsonElement = request(
		method = HTTP.Method.Post,
		endpoint = "webhooks/$identifier/$token?wait=$shouldWait",
		payload = JSON.encodeToJsonElement( WebhookMessageBuilder().apply( builderBlock ).apply { preventMentions() }.build() ) as JsonObject
	)
	suspend fun sendWebhookText( identifier: String, token: String, builderBlock: WebhookMessageBuilder.() -> Unit ): Message =
		JSON.decodeFromJsonElement( sendWebhookText( identifier, token, true, builderBlock ) )
	suspend fun sendWebhookTextWithoutWaiting( identifier: String, token: String, builderBlock: WebhookMessageBuilder.() -> Unit )
		{ sendWebhookText( identifier, token, false, builderBlock ) }

	private suspend fun sendWebhookEmbed( identifier: String, token: String, shouldWait: Boolean, embed: Embed ): JsonElement = request(
		method = HTTP.Method.Post,
		endpoint = "webhooks/$identifier/$token?wait=$shouldWait",
		payload = JSON.encodeToJsonElement( createWebhookMessage {
			preventMentions()
			embeds = listOf( embed )
		} ) as JsonObject
	)
	suspend fun sendWebhookEmbed( identifier: String, token: String, embed: Embed ): Message =
		JSON.decodeFromJsonElement( sendWebhookEmbed( identifier, token, true, embed ) )
	suspend fun sendWebhookEmbed( identifier: String, token: String, builderBlock: EmbedBuilder.() -> Unit ): Message =
		JSON.decodeFromJsonElement( sendWebhookEmbed( identifier, token, true, EmbedBuilder().apply( builderBlock ).build() ) )
	suspend fun sendWebhookEmbedWithoutWaiting( identifier: String, token: String, embed: Embed )
		{ sendWebhookEmbed( identifier, token, false, embed ) }
	suspend fun sendWebhookEmbedWithoutWaiting( identifier: String, token: String, builderBlock: EmbedBuilder.() -> Unit )
		{ sendWebhookEmbed( identifier, token, false, EmbedBuilder().apply( builderBlock ).build() ) }

	private suspend fun sendWebhookEmbedWithAttachment( identifier: String, token: String, shouldWait: Boolean, filePath: Path, embed: Embed ): JsonElement {
		val formData = createFormData {
			addTextSection {
				name = "payload_json"
				contentType = "application/json"
				value = JSON.encodeToString( createWebhookMessage {
					embeds = listOf( embed )
				} )
			}

			addBytesSection {
				name = "files[0]"
				parameters[ "filename" ] = filePath.fileName.toString()
				contentType = Files.probeContentType( filePath )
				value = Files.readAllBytes( filePath ).toMutableList()
			}
		}

		val headers = defaultHttpRequestHeaders.toMutableMap()
		headers[ "Content-Type" ] = "multipart/form-data; charset=utf-8; boundary=${ formData.boundary }"

		val httpResponse = HTTP.request( "POST", "$apiBaseUrl/webhooks/$identifier/$token?wait=$shouldWait", headers, null, formData )
		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return if ( httpResponse.statusCode() == 204 ) JSON.encodeToJsonElement( "" ) else JSON.decodeFromString( httpResponse.body() )
	}
	suspend fun sendWebhookEmbedWithAttachment( identifier: String, token: String, filePath: Path, embed: Embed ): Message =
		JSON.decodeFromJsonElement( sendWebhookEmbedWithAttachment( identifier, token, true, filePath, embed ) )
	suspend fun sendWebhookEmbedWithAttachment( identifier: String, token: String, filePath: Path, builderBlock: EmbedBuilder.() -> Unit ): Message =
		JSON.decodeFromJsonElement( sendWebhookEmbedWithAttachment( identifier, token, true, filePath, EmbedBuilder().apply( builderBlock ).build() ) )

	// TODO: These are useless - https://github.com/discord/discord-api-docs/issues/5099
	suspend fun sendWebhookEmbedWithAttachmentWithoutWaiting( identifier: String, token: String, filePath: Path, embed: Embed )
		{ sendWebhookEmbedWithAttachment( identifier, token, false, filePath, embed ) }
	suspend fun sendWebhookEmbedWithAttachmentWithoutWaiting( identifier: String, token: String, filePath: Path, builderBlock: EmbedBuilder.() -> Unit )
		{ sendWebhookEmbedWithAttachment( identifier, token, false, filePath, EmbedBuilder().apply( builderBlock ).build() ) }
}
