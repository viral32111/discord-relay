package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress( "UNUSED" )
class Server: DedicatedServerModInitializer {

	companion object {
		private const val MOD_ID = "progression"
		val LOGGER: Logger = LoggerFactory.getLogger( "com.viral32111.$MOD_ID" )

		@OptIn( ExperimentalSerializationApi::class )
		val JSON = Json {
			prettyPrint = true
			prettyPrintIndent = "\t"
			ignoreUnknownKeys = true
		}

		const val CONFIGURATION_DIRECTORY_NAME = "viral32111"
		const val CONFIGURATION_FILE_NAME = "$MOD_ID.json"

		var configuration = Configuration()

		/**
		 * Gets the current version of this mod.
		 * @since 0.6.0
		 */
		fun getModVersion(): String =
			FabricLoader.getInstance().getModContainer( MOD_ID ).orElseThrow {
				throw IllegalStateException( "Mod container not found" )
			}.metadata.version.friendlyString
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ getModVersion() } initialized on the server." )
	}

}
