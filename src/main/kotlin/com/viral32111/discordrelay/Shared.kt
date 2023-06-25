package com.viral32111.discordrelay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn( ExperimentalSerializationApi::class )
val PrettyJSON = Json {
	prettyPrint = true
	prettyPrintIndent = "\t"
	ignoreUnknownKeys = true
}

val JSON = Json {
	prettyPrint = false
	ignoreUnknownKeys = true
}
