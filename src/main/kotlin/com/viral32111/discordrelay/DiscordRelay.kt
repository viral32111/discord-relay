package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.Gateway
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

@Suppress( "UNUSED" )
class DiscordRelay: DedicatedServerModInitializer {
	companion object {
		const val MOD_ID = "discordrelay"
		val LOGGER: Logger = LoggerFactory.getLogger( "com.viral32111.$MOD_ID" )

		const val CONFIGURATION_DIRECTORY_NAME = "viral32111"
		const val CONFIGURATION_FILE_NAME = "$MOD_ID.json"

		var configuration = Configuration()
		var gateway: Gateway? = null

		val coroutineScope = CoroutineScope( Dispatchers.IO )
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ Version.discordRelay() } initialized on the server." )

		configuration = loadConfigurationFile()
		if ( configuration.discord.application.token.isBlank() ) throw RuntimeException( "Discord application token is blank" )
		if ( configuration.discord.api.baseUrl.isBlank() ) throw RuntimeException( "Discord API base URL is blank" )
		if ( configuration.discord.api.version <= 0 ) throw RuntimeException( "Discord API version is invalid" )

		HTTP.initialize( configuration )
		API.initialize( configuration )

		registerWebhookCallbackListeners( configuration )

		ServerLifecycleEvents.SERVER_STOPPED.register {
			coroutineScope.cancel()
		}

		coroutineScope.launch {
			val gatewayWebSocketUrl = API.getGateway().url
			LOGGER.info( "Discord Gateway URL: '${ gatewayWebSocketUrl }'" )

			gateway = Gateway( gatewayWebSocketUrl, configuration )
		}
	}

	private fun loadConfigurationFile(): Configuration {
		val serverConfigurationDirectory = FabricLoader.getInstance().configDir
		val configurationDirectory = serverConfigurationDirectory.resolve( CONFIGURATION_DIRECTORY_NAME )
		val configurationFile = configurationDirectory.resolve( CONFIGURATION_FILE_NAME )

		if ( configurationDirectory.notExists() ) {
			configurationDirectory.createDirectory()
			LOGGER.info( "Created directory '${ configurationDirectory }' for configuration files." )
		}

		if ( configurationFile.notExists() ) {
			val configAsJSON = PrettyJSON.encodeToString( Configuration() )

			configurationFile.writeText( configAsJSON, options = arrayOf(
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			) )

			LOGGER.info( "Created configuration file '${ configurationFile }'." )
		}


		// Warn about the old configuration file
		if ( serverConfigurationDirectory.resolve( "DiscordRelay.json" ).exists() ) {
			LOGGER.warn( "The old configuration file exists! Values should be moved to '${ configurationFile }'." )
		}

		val configAsJSON = configurationFile.readText()
		val config = PrettyJSON.decodeFromString<Configuration>( configAsJSON )
		LOGGER.info( "Loaded configuration from file '${ configurationFile }'" )

		return config
	}
}
