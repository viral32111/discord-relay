package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Gateway
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.net.URI

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
		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint" )
		DiscordRelay.LOGGER.info( "HTTP Response ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } byte(s))" )

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	private suspend fun request( endpoint: String, payload: JsonObject, method: String = "GET" ): JsonElement {
		val httpRequestHeaders = defaultHttpRequestHeaders.toMutableMap()
		httpRequestHeaders[ "Content-Type" ] = "application/json"

		val httpResponse = HTTP.request( method, "$apiBaseUrl/$endpoint", httpRequestHeaders, JSON.encodeToString( payload ) )
		DiscordRelay.LOGGER.info( "HTTP Response ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } byte(s))" )

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		return JSON.decodeFromString( httpResponse.body() )
	}

	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request(
		method = "GET",
		endpoint = "gateway/bot"
	) )

	data class HttpException( val responseStatusCode: Int, val requestMethod: String, val requestUri: URI ) : Exception() {
		override val message: String get() = "$requestMethod '$requestUri' -> $responseStatusCode"
	}
}
