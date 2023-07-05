package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.Gateway
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.encodeToString
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

// TODO: Player's client information in player join #log embed
// TODO: VPN/IP lookup in player join #log embed
// TODO: Server whitelist status in server started #log embed
// TODO: Discord category status
// TODO: API call rate limiting
// TODO: Check Gateway 4xxx close codes for resume/reconnect
// TODO: Use role color for name color in relayed message

@Suppress( "UNUSED" )
class DiscordRelay: DedicatedServerModInitializer {
	private val coroutineScope = CoroutineScope( Dispatchers.IO )
	private var configuration = Configuration()

	companion object {
		const val MOD_ID = "discordrelay"
		val LOGGER: Logger = LoggerFactory.getLogger( "com.viral32111.$MOD_ID" )

		const val CONFIGURATION_DIRECTORY_NAME = "viral32111"
		const val CONFIGURATION_FILE_NAME = "$MOD_ID.json"
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ Version.discordRelay() } initialized on the server." )

		configuration = loadConfigurationFile()
		if ( configuration.discord.application.token.isBlank() ) throw RuntimeException( "Discord application token is blank" )
		if ( configuration.discord.api.baseUrl.isBlank() ) throw RuntimeException( "Discord API base URL is blank" )
		if ( configuration.discord.api.version <= 0 ) throw RuntimeException( "Discord API version is invalid" )

		HTTP.initialize( configuration )
		API.initialize( configuration )

		registerWebhookCallbackListeners( coroutineScope, configuration )

		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			val gateway = Gateway( configuration, server.playerManager )

			coroutineScope.launch {
				val gatewayUrl = API.getGateway().url
				LOGGER.debug( "Discord Gateway URL: '${ gatewayUrl }'" )

				do {
					LOGGER.info( "Opening Discord Gateway connection..." )
					gateway.open( gatewayUrl )
					val confirmation = gateway.awaitClosure()
					LOGGER.info( "Discord Gateway connection closed." )
				} while ( confirmation?.isServerStopping != true )
			}

			ServerLifecycleEvents.SERVER_STOPPING.register {
				coroutineScope.launch {
					LOGGER.info( "Closing Discord Gateway connection..." )
					gateway.close( WebSocketCloseCode.Normal, "Server stopping.", true )
				}
			}
		}

		ServerLifecycleEvents.SERVER_STOPPED.register {
			coroutineScope.cancel()
		}
	}

	@OptIn( ExperimentalSerializationApi::class )
	private fun loadConfigurationFile(): Configuration {
		val serverConfigurationDirectory = FabricLoader.getInstance().configDir
		val configurationDirectory = serverConfigurationDirectory.resolve( CONFIGURATION_DIRECTORY_NAME )
		val configurationFile = configurationDirectory.resolve( CONFIGURATION_FILE_NAME )

		if ( configurationDirectory.notExists() ) {
			configurationDirectory.createDirectory()
			LOGGER.info( "Created directory '$configurationDirectory' for configuration files." )
		}

		if ( configurationFile.notExists() ) {
			val configAsJSON = PrettyJSON.encodeToString( Configuration() )

			configurationFile.writeText( configAsJSON, options = arrayOf(
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			) )

			LOGGER.info( "Created configuration file '$configurationFile'." )
		}

		// Warn about the old configuration file
		if ( serverConfigurationDirectory.resolve( "DiscordRelay.json" ).exists() ) {
			LOGGER.warn( "The old configuration file exists! Values should be moved to '$configurationFile'." )
		}

		val configAsJSON = configurationFile.readText()

		return try {
			val config = PrettyJSON.decodeFromString<Configuration>( configAsJSON )
			LOGGER.info( "Loaded configuration from file '$configurationFile'" )

			config
		} catch ( exception: MissingFieldException ) {
			LOGGER.error( "Configuration file '$configurationFile' missing required properties: ${ exception.missingFields.joinToString( ", " ) }" )

			Configuration()
		}
	}
}
