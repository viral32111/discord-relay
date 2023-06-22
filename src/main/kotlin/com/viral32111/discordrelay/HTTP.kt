package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object HTTP {
	private lateinit var httpClient: HttpClient
	private val defaultHttpRequestHeaders: MutableMap<String, String> = mutableMapOf(
		"Accept" to "*/*"
	)

	fun initialize( configuration: Configuration ) {
		httpClient = HttpClient.newBuilder()
			.connectTimeout( Duration.ofSeconds( configuration.http.timeoutSeconds.toLong() ) )
			.build()

		defaultHttpRequestHeaders[ "User-Agent" ] = arrayOf(
			configuration.http.userAgentPrefix,
			"Discord Relay/${ Version.discordRelay() }",
			"Events/${ Version.events() }",
			"Fabric Language Kotlin/${ Version.fabricLanguageKotlin() }",
			"Fabric API/${ Version.fabricAPI() }",
			"Fabric Loader/${ Version.fabricLoader() }",
			"Minecraft/${ Version.minecraft() }",
			"Java/${ Version.java() }"
		).joinToString( " " )
		DiscordRelay.LOGGER.info( "HTTP User Agent: '${ defaultHttpRequestHeaders[ "User-Agent" ] }'" )

		defaultHttpRequestHeaders[ "From" ] = configuration.http.fromAddress
		DiscordRelay.LOGGER.info( "HTTP From Address: '${ defaultHttpRequestHeaders[ "From" ] }'" )
	}

	suspend fun request( method: String, url: String, headers: Map<String, String>? = null, body: String? = null ): HttpResponse<String> {
		if ( !::httpClient.isInitialized ) throw IllegalStateException( "HTTP client not initialized" )
		if ( body != null && headers?.containsKey( "Content-Type" ) == false ) throw IllegalArgumentException( "HTTP content type header required for body" )

		val uri = URI.create( url )
		val bodyPublisher = if ( !body.isNullOrBlank() ) HttpRequest.BodyPublishers.ofString( body ) else HttpRequest.BodyPublishers.noBody()

		val httpRequestBuilder = HttpRequest.newBuilder()
			.timeout( httpClient.connectTimeout().get() )
			.method( method, bodyPublisher )
			.uri( uri )

		defaultHttpRequestHeaders.forEach( httpRequestBuilder::header )
		headers?.forEach( httpRequestBuilder::header )

		val httpRequest = httpRequestBuilder.build()
		DiscordRelay.LOGGER.info( "HTTP Request: ${ httpRequest.method() } '${ httpRequest.uri() }' '${ httpRequest.bodyPublisher().get() }' (${ httpRequest.bodyPublisher().get().contentLength() } bytes)" )

		return httpClient.sendAsync( httpRequest, HttpResponse.BodyHandlers.ofString() ).await()
	}
}
