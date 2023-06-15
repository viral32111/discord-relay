package com.viral32111.discordrelay

import com.viral32111.discordrelay.callback.PlayerAdvancementCompletedCallback
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.helper.Version
import com.viral32111.events.callback.server.PlayerDeathCallback
import com.viral32111.events.callback.server.PlayerJoinCallback
import com.viral32111.events.callback.server.PlayerLeaveCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.ActionResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.net.InetSocketAddress
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

@Suppress( "UNUSED" )
class DiscordRelay: DedicatedServerModInitializer {

	companion object {
		const val MOD_ID = "discordrelay"
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
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ Version.discordRelay() } initialized on the server." )

		configuration = loadConfigurationFile()
		if ( configuration.discord.application.token.isBlank() ) throw RuntimeException( "Discord application token is empty/whitespace" )
		if ( configuration.discord.api.baseUrl.isBlank() ) throw RuntimeException( "Discord API base URL is empty/whitespace" )
		if ( configuration.discord.api.version <= 0 ) throw RuntimeException( "Discord API version is invalid" )

		API.initializeHttpClient( configuration )

		registerCallbackListeners()
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
			val configAsJSON = JSON.encodeToString( Configuration() )

			configurationFile.writeText( configAsJSON, options = arrayOf(
				StandardOpenOption.CREATE_NEW,
				StandardOpenOption.WRITE
			) )

			LOGGER.info( "Created configuration file '${ configurationFile }'." )
		}


		// Warn about the old configuration file
		if ( serverConfigurationDirectory.resolve( "DiscordRelay.json" ).exists() ) {
			LOGGER.warn( "The old configuration file exists! Values should be moved to '${ configurationFile }'." );
		}

		val configAsJSON = configurationFile.readText()
		val config = JSON.decodeFromString<Configuration>( configAsJSON )
		LOGGER.info( "Loaded configuration from file '${ configurationFile }'" )

		return config
	}

	private fun registerCallbackListeners() {
		ServerLifecycleEvents.SERVER_STARTING.register { server ->
			LOGGER.info( "Server (${ server.serverModName }) starting" )

			CoroutineScope( Dispatchers.IO ).launch {
				try {
					val gatewayInfo = API.getGateway()
					LOGGER.info( gatewayInfo.url )
				} catch ( exception: API.HttpException ) {
					LOGGER.error( "HTTP Exception: ${ exception.message }" )
				} catch ( exception: Exception ) {
					LOGGER.error( "Exception: ${ exception.message }" )
				}

			}
		}

		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			LOGGER.info( "Server '${ server.serverIp }:${ server.serverPort }' (${ server.serverModName }) started" )
		}

		ServerLifecycleEvents.SERVER_STOPPING.register { server ->
			LOGGER.info( "Server '${ server.serverIp }:${ server.serverPort }' (${ server.serverModName }) stopped" )
		}

		ServerMessageEvents.CHAT_MESSAGE.register { message, player, parameters ->
			LOGGER.info( "Player '${ player.name.string }' (${ player.uuidAsString }) sent message '${ message.content.string }' (Signed: ${ message.hasSignature() }, Param. Name: ${ parameters.name }, Param. Type: ${ parameters.type })" )
		}

		PlayerJoinCallback.EVENT.register { connection, player ->
			val address = connection.address as InetSocketAddress
			LOGGER.info( "Player '${ player.name.string }' (${ player.uuidAsString }) joined from ${ address.address.hostAddress }:${ address.port }" )
			ActionResult.PASS
		}

		PlayerLeaveCallback.EVENT.register { player ->
			LOGGER.info( "Player '${ player.name.string }' (${ player.uuidAsString }) left" )
			ActionResult.PASS
		}

		PlayerDeathCallback.EVENT.register { player, damageSource ->
			LOGGER.info( "Player '${ player.name.string }' (${ player.uuidAsString }) died due to '${ damageSource.source?.name?.string }'" )
			ActionResult.PASS
		}

		PlayerAdvancementCompletedCallback.EVENT.register { player, advancement, criterionName, shouldAnnounceToChat ->
			LOGGER.info( "Player '${ player.name.string }' (${ player.uuidAsString }) completed advancement '${ advancement.display?.title?.string }' (Criterion: '${ criterionName }', Chat Announce: ${ shouldAnnounceToChat })" )
			ActionResult.PASS
		}
	}

}
