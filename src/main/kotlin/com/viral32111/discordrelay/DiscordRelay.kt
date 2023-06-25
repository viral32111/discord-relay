package com.viral32111.discordrelay

import com.viral32111.discordrelay.callback.PlayerAdvancementCompletedCallback
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.Gateway
import com.viral32111.discordrelay.discord.data.EmbedAuthor
import com.viral32111.discordrelay.discord.data.EmbedField
import com.viral32111.discordrelay.discord.data.EmbedFooter
import com.viral32111.discordrelay.discord.data.EmbedThumbnail
import com.viral32111.discordrelay.helper.*
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
import net.minecraft.advancement.AdvancementFrame
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.world.World
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.*
import kotlin.io.path.*
import kotlin.math.roundToInt

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

		var serverStartTime: Instant = Instant.now()
		val playerIPAddresses: MutableMap<UUID, String> = mutableMapOf()
		var gateway: Gateway? = null
	}

	override fun onInitializeServer() {
		LOGGER.info( "Discord Relay v${ Version.discordRelay() } initialized on the server." )

		configuration = loadConfigurationFile()
		if ( configuration.discord.application.token.isBlank() ) throw RuntimeException( "Discord application token is empty/whitespace" )
		if ( configuration.discord.api.baseUrl.isBlank() ) throw RuntimeException( "Discord API base URL is empty/whitespace" )
		if ( configuration.discord.api.version <= 0 ) throw RuntimeException( "Discord API version is invalid" )

		HTTP.initialize( configuration )
		API.initialize( configuration )

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
			LOGGER.warn( "The old configuration file exists! Values should be moved to '${ configurationFile }'." )
		}

		val configAsJSON = configurationFile.readText()
		val config = JSON.decodeFromString<Configuration>( configAsJSON )
		LOGGER.info( "Loaded configuration from file '${ configurationFile }'" )

		return config
	}

	private fun registerCallbackListeners() {
		ServerLifecycleEvents.SERVER_STARTING.register { _ ->
			CoroutineScope( Dispatchers.IO ).launch {
				val gatewayWebSocketUrl = API.getGateway().url
				LOGGER.info( "Discord Gateway URL: '${ gatewayWebSocketUrl }'" )

				gateway = Gateway( gatewayWebSocketUrl, configuration )
			}
		}

		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			serverStartTime = Instant.now()
			val publicServerAddress = configuration.publicAddress.ifBlank { "${ server.serverIp }:${ server.serverPort }" }
			val serverUrl = configuration.thirdParty.serverUrl.format( publicServerAddress )

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor( "The server is now open!" )
					description = "Join at [`$publicServerAddress`]($serverUrl)."
					color = 0x00FF00
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				val iconFile = Path( server.runDirectory.absolutePath, "server-icon.png" )

				if ( iconFile.exists() ) API.sendWebhookEmbedWithAttachment( logWebhookIdentifier, logWebhookToken, iconFile ) {
					title = "Server Started"
					fields = listOf(
						EmbedField( name = "IP Address", value = "[`$publicServerAddress`]($serverUrl)", inline = false ),
						EmbedField( name = "Version", value = server.version, inline = true ),
						EmbedField( name = "Brand", value = server.serverModName, inline = true )
					)
					thumbnail = EmbedThumbnail( url = "attachment://${ iconFile.fileName }" )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0x00FF00
				} else API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Server Started"
					fields = listOf(
						EmbedField( name = "IP Address", value = "[`$publicServerAddress`]($serverUrl)", inline = false ),
						EmbedField( name = "Version", value = server.version, inline = true ),
						EmbedField( name = "Brand", value = server.serverModName, inline = true )
					)
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0x00FF00
				}
			}

			CoroutineScope( Dispatchers.IO ).launch {
				gateway?.open()
			}
		}

		ServerLifecycleEvents.SERVER_STOPPING.register { server ->
			val serverUptime = ( Instant.now().epochSecond - serverStartTime.epochSecond ).toHourMinuteSecond()

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor( "The server has closed." )
					description = "Open for ${ serverUptime }."
					color = 0xFF0000
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				val iconFile = Path( server.runDirectory.path, "server-icon.png" )

				if ( iconFile.exists() ) API.sendWebhookEmbedWithAttachment( logWebhookIdentifier, logWebhookToken, iconFile ) {
					title = "Server Stopped"
					fields = listOf(
						EmbedField( name = "Started At", value = serverStartTime.formatInUTC( configuration.dateTimeFormat ), inline = false ),
						EmbedField( name = "Uptime", value = serverUptime, inline = false )
					)
					thumbnail = EmbedThumbnail( url = "attachment://${ iconFile.fileName }" )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFF0000
				} else API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Server Stopped"
					fields = listOf(
						EmbedField( name = "Started At", value = serverStartTime.formatInUTC( configuration.dateTimeFormat ), inline = true ),
						EmbedField( name = "Uptime", value = serverUptime, inline = true )
					)
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFF0000
				}
			}

			CoroutineScope( Dispatchers.IO ).launch {
				gateway?.close()
			}
		}

		ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookText( relayWebhookIdentifier, relayWebhookToken ) {
					avatarUrl = configuration.thirdParty.avatarUrl.format( player.uuidAsString )
					userName = player.name.string
					content = message.content.string
				}
			}
		}

		PlayerJoinCallback.EVENT.register { connection, player ->
			val playerIPAddress = ( connection.address as InetSocketAddress ).address.hostAddress
			playerIPAddresses[ player.uuid ] = playerIPAddress

			val playerProfileUrl = configuration.thirdParty.profileUrl.format( player.uuidAsString )
			val playerAvatarUrl = configuration.thirdParty.avatarUrl.format( player.uuidAsString )

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor(
						name = "${ player.name.string } joined.",
						url = playerProfileUrl,
						iconUrl = playerAvatarUrl
					)
					color = 0xFFFFFF
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Player Joined"
					fields = listOf(
						EmbedField( name = "Name", value = player.name.string, inline = true ),
						EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
						EmbedField( name = "IP Address", value = "[`$playerIPAddress`](${ configuration.thirdParty.ipAddressUrl.format( playerIPAddress ) })", inline = true ),
						EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
						EmbedField( name = "Position", value = "${ player.pos.getX().roundToInt() }, ${ player.pos.getY().roundToInt() }, ${ player.pos.getZ().roundToInt() }", inline = true ),
						EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
					)
					thumbnail = EmbedThumbnail( url = playerAvatarUrl )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFFFFFF
				}
			}

			ActionResult.PASS
		}

		PlayerLeaveCallback.EVENT.register { player ->
			val playerIPAddress = playerIPAddresses.getOrDefault( player.uuid, "*Unknown*" )

			val playerProfileUrl = configuration.thirdParty.profileUrl.format( player.uuidAsString )
			val playerAvatarUrl = configuration.thirdParty.avatarUrl.format( player.uuidAsString )

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor(
						name = "${ player.name.string } left.",
						url = playerProfileUrl,
						iconUrl = playerAvatarUrl
					)
					color = 0xFFFFFF
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Player Left"
					fields = listOf(
						EmbedField( name = "Name", value = player.name.string, inline = true ),
						EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
						EmbedField( name = "IP Address", value = "[`$playerIPAddress`](${ configuration.thirdParty.ipAddressUrl.format( playerIPAddress ) })", inline = true ),
						EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
						EmbedField( name = "Position", value = "${ player.pos.getX().roundToInt() }, ${ player.pos.getY().roundToInt() }, ${ player.pos.getZ().roundToInt() }", inline = true ),
						EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
					)
					thumbnail = EmbedThumbnail( url = playerAvatarUrl )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFFFFFF
				}
			}

			ActionResult.PASS
		}

		PlayerDeathCallback.EVENT.register { player, damageSource ->
			val deathMessage = "${ damageSource.getDeathMessage( player ).string }."

			val playerProfileUrl = configuration.thirdParty.profileUrl.format( player.uuidAsString )
			val playerAvatarUrl = configuration.thirdParty.avatarUrl.format( player.uuidAsString )

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor(
						name = deathMessage,
						url = playerProfileUrl,
						iconUrl = playerAvatarUrl
					)
					color = 0xFFFFAA
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Player Died"
					fields = listOf(
						EmbedField( name = "Name", value = player.name.string, inline = true ),
						EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
						EmbedField( name = "Attacker", value = damageSource.attacker?.name?.string ?: "*Unknown*", inline = true ),
						EmbedField( name = "Source", value = damageSource.source?.name?.string  ?: "*Unknown*", inline = true ),
						EmbedField( name = "Message", value = deathMessage, inline = false ),
						EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
						EmbedField( name = "Position", value = "${ player.pos.getX().roundToInt() }, ${ player.pos.getY().roundToInt() }, ${ player.pos.getZ().roundToInt() }", inline = true ),
						EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
					)
					thumbnail = EmbedThumbnail( url = playerAvatarUrl )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFFFFAA
				}
			}

			ActionResult.PASS
		}

		PlayerAdvancementCompletedCallback.EVENT.register { player, advancement, _, shouldAnnounceToChat ->
			if ( !shouldAnnounceToChat ) return@register ActionResult.PASS

			val advancementText = when ( advancement.display?.frame ) {
				AdvancementFrame.TASK -> "has made the advancement"
				AdvancementFrame.CHALLENGE -> "completed the challenge"
				AdvancementFrame.GOAL -> "reached the goal"
				else -> null
			}

			val advancementType = when ( advancement.display?.frame ) {
				AdvancementFrame.TASK -> "Advancement"
				AdvancementFrame.CHALLENGE -> "Challenge"
				AdvancementFrame.GOAL -> "Goal"
				else -> null
			}

			val advancementColor = when ( advancement.display?.frame ) {
				AdvancementFrame.CHALLENGE -> 0xA700A7 // Challenge Purple
				else -> 0x54FB54 // Advancement Green
			}

			val playerProfileUrl = configuration.thirdParty.profileUrl.format( player.uuidAsString )
			val playerAvatarUrl = configuration.thirdParty.avatarUrl.format( player.uuidAsString )

			val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
			val relayWebhookToken = configuration.discord.channels.relay.webhook.token
			if ( relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
					author = EmbedAuthor(
						name = "${ player.name.string } ${ advancementText ?: "gained the achievement" } ${ advancement.display?.title?.string ?: "Unknown" }.",
						url = playerProfileUrl,
						iconUrl = playerAvatarUrl
					)
					color = advancementColor
				}
			}

			val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
			val logWebhookToken = configuration.discord.channels.log.webhook.token
			if ( logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank() ) CoroutineScope( Dispatchers.IO ).launch {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Player Completed Advancement"
					fields = listOf(
						EmbedField( name = "Name", value = player.name.string, inline = true ),
						EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
						EmbedField( name = "Title", value = advancement.display?.title?.string ?: "*Unknown*", inline = true ),
						EmbedField( name = "Type", value = advancementType ?: "*Unknown*", inline = true ),
						EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
						EmbedField( name = "Position", value = "${ player.pos.getX().roundToInt() }, ${ player.pos.getY().roundToInt() }, ${ player.pos.getZ().roundToInt() }", inline = true ),
						EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
					)
					thumbnail = EmbedThumbnail( url = playerAvatarUrl )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = advancementColor
				}
			}

			ActionResult.PASS
		}
	}

	private fun ServerPlayerEntity.getDimensionName(): String =
		when ( val dimension = world.registryKey ) {
			World.OVERWORLD -> "Overworld"
			World.NETHER -> "The Nether"
			World.END -> "The End"
			else -> dimension.toString()
		}

	private fun ServerPlayerEntity.getNickName(): String? =
		displayName.string.takeUnless { it == name.string }

}
