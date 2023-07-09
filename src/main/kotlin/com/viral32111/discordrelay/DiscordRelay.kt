package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.Gateway
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

// TODO: Player kick/ban/pardon in #log embed
// TODO: Banned player attempted join in #log embed
// TODO: Base mod for shared code (HTTP, configuration, version & time helpers, extensions methods, etc.)

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

		HTTP.initialize( configuration )
		API.initialize( configuration )
		ProxyCheck.initialize( configuration )

		if ( configuration.discord.application.token.isNotBlank() ) {
			registerCallbackListeners( coroutineScope, configuration )

			ServerLifecycleEvents.SERVER_STARTED.register { server ->
				val gateway = Gateway( configuration, server.playerManager )

				coroutineScope.launch {
					val gatewayUrl = API.getGateway().url
					LOGGER.debug( "Discord Gateway URL: '$gatewayUrl'" )

					do {
						var confirmation: Gateway.ClosureConfirmation? = null

						try {
							LOGGER.info( "Opening Discord Gateway connection..." )
							gateway.open( gatewayUrl )
							confirmation = gateway.awaitClosure()
							LOGGER.info( "Discord Gateway connection closed." )
						} catch ( exception: ConnectException ) {
							LOGGER.error( "Discord Gateway failed to connect! ($exception)" )
						} catch ( exception: UnresolvedAddressException ) {
							LOGGER.error( "Unable to resolve Discord Gateway hostname! ($exception)" )
						} catch ( exception: Exception ) {
							LOGGER.error( "Discord Gateway encountered an error! ($exception)" )
						}

						LOGGER.debug( "Waiting 30 seconds before reconnecting..." )
						delay( 30000 ) // Wait 30 seconds
					} while ( confirmation?.isServerStopping != true )
				}

				ServerLifecycleEvents.SERVER_STOPPING.register {
					coroutineScope.launch {
						LOGGER.info( "Closing Discord Gateway connection..." )
						gateway.close( WebSocketCloseCode.Normal, "Server stopping.", true )
					}
				}
			}
		}

		ServerLifecycleEvents.SERVER_STOPPED.register {
			coroutineScope.cancel()
		}
	}

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
		val config = PrettyJSON.decodeFromString<Configuration>( configAsJSON )
		LOGGER.info( "Loaded configuration from file '$configurationFile'" )

		return config
	}
}
