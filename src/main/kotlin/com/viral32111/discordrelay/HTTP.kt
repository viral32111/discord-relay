package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.future.await
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

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
			"Fabric Language Kotlin/${ Version.fabricLanguageKotlin( true ) }",
			"Fabric API/${ Version.fabricAPI( true ) }",
			"Fabric Loader/${ Version.fabricLoader() }",
			"Minecraft/${ Version.minecraft() }",
			"Java/${ Version.java() }"
		).joinToString( " " )
		DiscordRelay.LOGGER.info( "HTTP User Agent: '${ defaultHttpRequestHeaders[ "User-Agent" ] }'" )

		defaultHttpRequestHeaders[ "From" ] = configuration.http.fromAddress
		DiscordRelay.LOGGER.info( "HTTP From Address: '${ defaultHttpRequestHeaders[ "From" ] }'" )
	}

	suspend fun request( method: String, url: String, headers: Map<String, String>? = null, body: String? = null, formData: FormData? = null ): HttpResponse<String> {
		if ( !::httpClient.isInitialized ) throw IllegalStateException( "HTTP client not initialized" )
		if ( body != null && headers?.containsKey( "Content-Type" ) == false ) throw IllegalArgumentException( "HTTP content type header required for body" )

		val uri = URI.create( url )
		val bodyPublisher = if ( formData != null ) HttpRequest.BodyPublishers.ofByteArray( formData.toByteArray() )
			else if ( !body.isNullOrBlank() ) HttpRequest.BodyPublishers.ofString( body )
			else HttpRequest.BodyPublishers.noBody()

		val httpRequestBuilder = HttpRequest.newBuilder()
			.timeout( httpClient.connectTimeout().get() )
			.method( method, bodyPublisher )
			.uri( uri )

		defaultHttpRequestHeaders.forEach( httpRequestBuilder::header )
		headers?.forEach( httpRequestBuilder::header )

		val httpRequest = httpRequestBuilder.build()
		DiscordRelay.LOGGER.info( "HTTP Request: ${ httpRequest.method() } '${ httpRequest.uri() }' '${ body.orEmpty() }' (${ httpRequest.bodyPublisher().get().contentLength() } bytes)" )

		val httpResponse = httpClient.sendAsync( httpRequest, HttpResponse.BodyHandlers.ofString() ).await()
		DiscordRelay.LOGGER.info( "HTTP Response: ${ httpResponse.statusCode() } '${ httpResponse.body() }' (${ httpResponse.body().length } bytes)" )

		return httpResponse
	}

	suspend fun createWebSocket( uri: URI, listener: WebSocket.Listener, configuration: Configuration ): WebSocket = httpClient.newWebSocketBuilder()
			.connectTimeout( Duration.ofSeconds( configuration.http.timeoutSeconds.toLong() ) )
			.buildAsync( uri, listener )
			.await()

	data class HttpException(
		val responseStatusCode: Int,
		val requestMethod: String,
		val requestUri: URI
	) : Exception() {
		override val message: String get() = "$requestMethod '$requestUri' -> $responseStatusCode"
	}

	data class FormData(
		val boundary: String,
		val textSections: List<FormDataSectionText>,
		val bytesSections: List<FormDataSectionBytes>
	) {
		fun toByteArray(): ByteArray {
			val crlf = "\r\n"
			val structure = ByteArrayOutputStream()
			structure.write( "--$boundary$crlf" )

			textSections.forEachIndexed { index, it ->
				val parameters = it.parameters.map { parameter -> "${ parameter.key }=\"${ parameter.value }\"" }.joinToString( "; " )

				structure.write( "Content-Type: ${ it.contentType }$crlf" )
				structure.write( "Content-Disposition: form-data; name=\"${ it.name }\"${ if ( parameters.isNotBlank() ) "; $parameters" else "" }$crlf$crlf" )
				structure.write( it.value )
				structure.write( if ( index != ( textSections.size - 1 ) || bytesSections.isNotEmpty() ) "$crlf--$boundary$crlf" else "$crlf--$boundary--$crlf" )
			}

			bytesSections.forEachIndexed { index, it ->
				val parameters = it.parameters.map { parameter -> "${ parameter.key }=\"${ parameter.value }\"" }.joinToString( "; " )

				structure.write( "Content-Type: ${ it.contentType }$crlf" )
				structure.write( "Content-Disposition: form-data; name=\"${ it.name }\"${ if ( parameters.isNotBlank() ) "; $parameters" else "" }$crlf$crlf" )
				structure.writeBytes( it.value.toByteArray() )
				structure.write( if ( index != ( bytesSections.size - 1 ) ) "$crlf--$boundary$crlf" else "$crlf--$boundary--$crlf" )
			}

			return structure.toByteArray()
		}

		override fun toString(): String = toByteArray().toString( Charsets.UTF_8 )
	}

	class FormDataBuilder {
		private var boundary = System.nanoTime().toString()
		private val textSections = mutableListOf<FormDataSectionText>()
		private val bytesSections = mutableListOf<FormDataSectionBytes>()

		fun addTextSection( block: FormDataSectionTextBuilder.() -> Unit ) = textSections.add( FormDataSectionTextBuilder().apply( block ).build() )
		fun addBytesSection( block: FormDataSectionBytesBuilder.() -> Unit ) = bytesSections.add( FormDataSectionBytesBuilder().apply( block ).build() )

		fun build() = FormData( boundary, textSections, bytesSections )
	}

	fun createFormData( block: FormDataBuilder.() -> Unit ) = FormDataBuilder().apply( block ).build()

	data class FormDataSectionText(
		val name: String,
		val value: String,
		val contentType: String,
		val parameters: Map<String, String>
	)

	class FormDataSectionTextBuilder {
		var name: String = ""
		var value: String = ""
		var contentType: String = "text/plain"
		private var parameters: MutableMap<String, String> = mutableMapOf()

		fun build() = FormDataSectionText( name, value, contentType, parameters )
	}


	data class FormDataSectionBytes(
		val name: String,
		val value: List<Byte>,
		val contentType: String,
		val parameters: Map<String, String>
	)

	class FormDataSectionBytesBuilder {
		var name: String = ""
		var value: MutableList<Byte> = mutableListOf()
		var contentType: String = "application/octet-stream"
		var parameters: MutableMap<String, String> = mutableMapOf()

		fun build() = FormDataSectionBytes( name, value, contentType, parameters )
	}
}

private fun ByteArrayOutputStream.write( str: String ) = this.writeBytes( str.toByteArray() )
