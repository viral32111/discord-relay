package com.viral32111.discordrelay.config

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProxyCheck(
	@Required val api: API = API()
) {

	@Serializable
	data class API(
		@Required @SerialName( "base-url" ) val baseUrl: String = "https://proxycheck.io",
		@Required val version: Int = 2,
		@Required val key: String = ""
	)

}
