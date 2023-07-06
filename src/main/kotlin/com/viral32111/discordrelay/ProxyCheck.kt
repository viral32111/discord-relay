package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress

object ProxyCheck {
	private lateinit var baseUrl: String
	private lateinit var key: String

	fun initialize( configuration: Configuration) {
		baseUrl = "${ configuration.thirdParty.proxyCheck.api.baseUrl }/v${ configuration.thirdParty.proxyCheck.api.version }"
		key = configuration.thirdParty.proxyCheck.api.key
		DiscordRelay.LOGGER.debug( "ProxyCheck API Base URL: '$baseUrl'" )
	}

	/**
	 * Checks if an IP address is within a local/private network range.
	 * @param ipAddress The IP address to check.
	 * @return If the IP address is local/private.
	 */
	fun isPrivate( ipAddress: String ): Boolean {
		val inetAddress = InetAddress.getByName( ipAddress )
		return inetAddress.isLoopbackAddress || inetAddress.isSiteLocalAddress || inetAddress.isAnyLocalAddress || inetAddress.isLinkLocalAddress
	}

	/**
	 * Fetches known information about an IP address, including location and if it is a VPN.
	 * @param ipAddress The IP address to fetch information for.
	 * @return The last known information about the given IP address.
	 */
	suspend fun check(ipAddress: String ): IPAddress {
		val httpResponse = HTTP.request( HTTP.Method.Get, "$baseUrl/$ipAddress", headers = mapOf(
			"Accept" to "application/json; */*"
		), parameters = mapOf(
			"vpn" to "3",
			"risk" to "1",
			"asn" to "1",
			"cur" to "0",
			"key" to key
		) )

		if ( httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300 ) throw HTTP.HttpException( httpResponse.statusCode(), httpResponse.request().method(), httpResponse.request().uri() )

		val apiResponse = JSON.decodeFromString<JsonObject>( httpResponse.body() )

		DiscordRelay.LOGGER.debug( "ProxyCheck Status: ${ apiResponse[ "status" ]?.jsonPrimitive?.content } " )
		val status = apiResponse[ "status" ]?.jsonPrimitive?.content ?: throw SerializationException( "ProxyCheck API response missing 'status' property (HTTP ${ httpResponse.statusCode() })" )
		if ( status != "ok" ) throw Exception( "ProxyCheck API said '${ apiResponse[ "message" ]?.jsonPrimitive?.content }' for IP address '$ipAddress' (HTTP ${ httpResponse.statusCode() })" )

		return JSON.decodeFromJsonElement( apiResponse[ ipAddress ] ?: throw SerializationException( "ProxyCheck API response missing '$ipAddress' property (HTTP ${ httpResponse.statusCode() })" ) )
	}

	@Serializable
	data class IPAddress(
		@Serializable( with = TypeSerializer::class ) val type: Type? = null, // https://proxycheck.io/api/#type_responses

		// vpn=3
		@Required @SerialName( "vpn" ) @Serializable( with = YesNoSerializer::class ) val isVPN: Boolean,
		@Required @SerialName( "proxy" ) @Serializable( with = YesNoSerializer::class ) val isProxy: Boolean,

		// risk=1
		@SerialName( "risk" ) @Required val riskScore: Int? = null, // https://proxycheck.io/api/#risk_score

		// asn=1 & cur=0
		@SerialName( "asn" ) val autonomousSystem: String? = null,
		@SerialName( "organisation" ) val organization: String? = null,
		@SerialName( "continent" ) val continentName: String? = null,
		@SerialName( "continentcode" ) val continentCode: String? = null,
		@SerialName( "country" ) val countryName: String? = null,
		@SerialName( "isocode" ) val countryCode: String? = null,
		@SerialName( "region" ) val regionName: String? = null,
		@SerialName( "regionCode" ) val regionCode: String? = null,
		@SerialName( "city" ) val cityName: String? = null,
		@SerialName( "timezone" ) val timeZone: String? = null,
		val latitude: Double? = null,
		val longitude: Double? = null,
	)

	@OptIn( ExperimentalSerializationApi::class )
	@Serializer( forClass = IPAddress::class )
	class YesNoSerializer: KSerializer<Boolean> {
		override fun serialize( encoder: Encoder, value: Boolean ) =
			encoder.encodeString( if ( value ) "yes" else "no" )

		override fun deserialize( decoder: Decoder ): Boolean =
			when ( val value = decoder.decodeString().lowercase() ) {
				"yes" -> true
				"no" -> false
				else -> throw SerializationException( "Invalid yes/no value '$value'" )
			}
	}

	@Serializable( with = TypeSerializer::class )
	enum class Type {
		Residential,
		Wireless,
		Business,
		Hosting,
		TOR,
		SOCKS,
		SOCKS4,
		SOCKS4A,
		SOCKS5,
		SOCKS5H,
		ShadowSocks,
		HTTP,
		HTTPS,
		CompromisedServer,
		InferenceEngine,
		OpenVPN,
		VPN
	}

	@OptIn( ExperimentalSerializationApi::class )
	@Serializer( forClass = IPAddress::class )
	object TypeSerializer : KSerializer<Type> {
		override fun serialize( encoder: Encoder, value: Type) =
			encoder.encodeString( when ( value ) {
				Type.ShadowSocks -> "Shadowsocks"
				Type.CompromisedServer -> "Compromised Server"
				Type.InferenceEngine -> "Inference Engine"
				else -> value.name
			} )

		override fun deserialize( decoder: Decoder ): Type =
			when ( val value = decoder.decodeString() ) {
				"Shadowsocks" -> Type.ShadowSocks
				"Compromised Server" -> Type.CompromisedServer
				"Inference Engine" -> Type.InferenceEngine
				else -> Type.valueOf( value )
			}
	}
}
