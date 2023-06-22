package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Gateway
import com.viral32111.discordrelay.helper.Version
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

object API {
	private lateinit var httpClient: HttpClient

	private val JSON = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}

	// https://ktor.io/docs/create-client.html
	fun initializeHttpClient( configuration: Configuration ) {
		val baseUrl = "${ configuration.discord.api.baseUrl }/v${ configuration.discord.api.version }/"
		val userAgent = arrayOf(
			configuration.http.userAgentPrefix,
			"Discord Relay/${ Version.discordRelay() }",
			"Events/${ Version.events() }",
			"Fabric Language Kotlin/${ Version.fabricLanguageKotlin() }",
			"Fabric API/${ Version.fabricAPI() }",
			"Fabric Loader/${ Version.fabricLoader() }",
			"Minecraft/${ Version.minecraft() }",
			"Java/${ Version.java() }"
		).joinToString( " " )

		DiscordRelay.LOGGER.info( "Discord API URL: '${ baseUrl }'" )
		DiscordRelay.LOGGER.info( "HTTP User Agent: '${ userAgent }'" )

		httpClient = HttpClient( CIO ) {

			// https://ktor.io/docs/serialization-client.html
			install( ContentNegotiation ) {
				json( JSON )
			}

			// https://ktor.io/docs/default-request.html
			install( DefaultRequest ) {
				url( baseUrl )

				accept( ContentType.Application.Json )
				userAgent( userAgent )

				headers {
					append( "From", configuration.http.fromAddress )
					append( "Authorization", "Bot ${ configuration.discord.application.token }" )
				}
			}

			// https://ktor.io/docs/timeout.html
			install( HttpTimeout ) {
				requestTimeoutMillis = configuration.http.timeoutSeconds * 1000L
				connectTimeoutMillis = configuration.http.timeoutSeconds * 1000L
				socketTimeoutMillis = configuration.http.timeoutSeconds * 1000L
			}

		}
	}

	private suspend fun request( method: HttpMethod, endpoint: String ): JsonElement {
		if ( !::httpClient.isInitialized ) throw IllegalStateException( "Ktor HTTP client is not initialized" )

		val httpResponse = httpClient.request( endpoint ) {
			this.method = method
		}

		DiscordRelay.LOGGER.info( "HTTP ${ httpResponse.request.method } '${ httpResponse.request.url }' -> ${ httpResponse.status.value }, ${ httpResponse.contentLength() } byte(s)" )

		if ( !httpResponse.status.isSuccess() ) throw HttpException( httpResponse.status, httpResponse.request.method, httpResponse.request.url )

		return httpResponse.body()
	}

	private suspend fun request( endpoint: String ) = request( HttpMethod.Companion.Get, endpoint )

	suspend fun getGateway(): Gateway = JSON.decodeFromJsonElement( request( "gateway/bot" ) )

	data class HttpException( val responseStatusCode: HttpStatusCode, val requestMethod: HttpMethod, val requestUrl: Url ) : Exception() {
		override val message: String
			get() = "$requestMethod '$requestUrl' -> ${ responseStatusCode.value }"
	}
}
