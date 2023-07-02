package com.viral32111.discordrelay

import com.viral32111.discordrelay.callback.PlayerAdvancementCompletedCallback
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.data.*
import com.viral32111.discordrelay.helper.formatInUTC
import com.viral32111.discordrelay.helper.getCurrentDateTimeISO8601
import com.viral32111.discordrelay.helper.getCurrentDateTimeUTC
import com.viral32111.discordrelay.helper.toHumanReadableTime
import com.viral32111.events.callback.server.PlayerDeathCallback
import com.viral32111.events.callback.server.PlayerJoinCallback
import com.viral32111.events.callback.server.PlayerLeaveCallback
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.advancement.AdvancementFrame
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Vec3d
import java.net.InetSocketAddress
import java.time.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.math.roundToInt

private var serverStartTime = Instant.now()
private val playerIPAddresses: MutableMap<UUID, String> = mutableMapOf()

fun registerWebhookCallbackListeners( configuration: Configuration ) {
	val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.id
	val relayWebhookToken = configuration.discord.channels.relay.webhook.token
	val isRelayChannelConfigured = relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank()

	val logWebhookIdentifier = configuration.discord.channels.log.webhook.id
	val logWebhookToken = configuration.discord.channels.log.webhook.token
	val isLogChannelConfigured = logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank()

	val avatarUrl = configuration.thirdParty.avatarUrl
	val profileUrl = configuration.thirdParty.profileUrl
	val serverUrl = configuration.thirdParty.serverUrl
	val ipAddressUrl = configuration.thirdParty.ipAddressUrl

	ServerLifecycleEvents.SERVER_STARTED.register { server ->
		serverStartTime = Instant.now()

		val serverPublicAddress = configuration.publicAddress.ifBlank { "${ server.serverIp }:${ server.serverPort }" }
		val serverPublicUrl = serverUrl.format( serverPublicAddress )

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor( "The server is now open!" )
				description = "Join at [`$serverPublicAddress`]($serverPublicUrl)."
				color = 0x00FF00 // Green
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			val serverIconFile = Path( server.runDirectory.absolutePath, configuration.serverIconFileName )

			val embedBuilder = EmbedBuilder().apply {
				title = "Server Started"
				fields = listOf(
					EmbedField( name = "IP Address", value = "[`$serverPublicAddress`]($serverPublicUrl)", inline = false ),
					EmbedField( name = "Version", value = server.version, inline = true ),
					EmbedField( name = "Brand", value = server.serverModName, inline = true )
				)
				footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
				timestamp = getCurrentDateTimeISO8601()
				color = 0x00FF00 // Green
			}

			if ( serverIconFile.exists() ) {
				embedBuilder.thumbnail = EmbedThumbnail( url = "attachment://${ serverIconFile.fileName }" )
				API.sendWebhookEmbedWithAttachment( logWebhookIdentifier, logWebhookToken, serverIconFile, embedBuilder.build() )
			} else {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken, embedBuilder.build() )
			}
		}
	}

	ServerLifecycleEvents.SERVER_STOPPING.register { server ->
		val serverUptime = ( Instant.now().epochSecond - serverStartTime.epochSecond ).toHumanReadableTime()

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor( "The server has closed." )
				description = "Open for $serverUptime."
				color = 0xFF0000 // Red
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			val serverIconFile = Path( server.runDirectory.absolutePath, configuration.serverIconFileName )

			val embedBuilder = EmbedBuilder().apply {
				title = "Server Stopped"
				fields = listOf(
					EmbedField( name = "Started At", value = serverStartTime.formatInUTC( configuration.dateTimeFormat ), inline = true ),
					EmbedField( name = "Uptime", value = serverUptime, inline = true )
				)
				footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
				timestamp = getCurrentDateTimeISO8601()
				color = 0xFF0000 // Red
			}

			if ( serverIconFile.exists() ) {
				embedBuilder.thumbnail = EmbedThumbnail( url = "attachment://${ serverIconFile.fileName }" )
				API.sendWebhookEmbedWithAttachment( logWebhookIdentifier, logWebhookToken, serverIconFile, embedBuilder.build() )
			} else {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken, embedBuilder.build() )
			}
		}
	}

	ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookText( relayWebhookIdentifier, relayWebhookToken ) {
				this.avatarUrl = avatarUrl.format( player.uuidAsString )
				userName = player.name.string
				content = message.content.string
			}
		}
	}

	PlayerJoinCallback.EVENT.register { connection, player ->
		val playerIPAddress = playerIPAddresses.put( player.uuid, ( connection.address as InetSocketAddress ).address.hostAddress )
		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } joined.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFFF // White
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Joined"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "IP Address", value = "[`$playerIPAddress`](${ ipAddressUrl.format( playerIPAddress ) })", inline = true ),
					EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
					EmbedField( name = "Position", value = player.pos.toHumanReadableString(), inline = true ),
					EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
				)
				thumbnail = EmbedThumbnail( url = playerAvatarUrl )
				footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
				timestamp = getCurrentDateTimeISO8601()
				color = 0xFFFFFF // White
			}
		}

		ActionResult.PASS
	}

	PlayerLeaveCallback.EVENT.register { player ->
		val playerIPAddress = playerIPAddresses.getOrDefault( player.uuid, "*Unknown*" )
		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } left.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFFF // White
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Left"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "IP Address", value = "[`$playerIPAddress`](${ ipAddressUrl.format( playerIPAddress ) })", inline = true ),
					EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
					EmbedField( name = "Position", value = player.pos.toHumanReadableString(), inline = true ),
					EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
				)
				thumbnail = EmbedThumbnail( url = playerAvatarUrl )
				footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
				timestamp = getCurrentDateTimeISO8601()
				color = 0xFFFFFF // White
			}
		}

		ActionResult.PASS
	}

	PlayerDeathCallback.EVENT.register { player, damageSource ->
		val deathMessage = "${ damageSource.getDeathMessage( player ).string }."

		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = deathMessage,
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFAA // Yellow-ish
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Died"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "Attacker", value = damageSource.attacker?.name?.string ?: "*Unknown*", inline = true ),
					EmbedField( name = "Source", value = damageSource.source?.name?.string  ?: "*Unknown*", inline = true ),
					EmbedField( name = "Message", value = deathMessage, inline = false ),
					EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
					EmbedField( name = "Position", value = player.pos.toHumanReadableString(), inline = true ),
					EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
				)
				thumbnail = EmbedThumbnail( url = playerAvatarUrl )
				footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
				timestamp = getCurrentDateTimeISO8601()
				color = 0xFFFFAA // Yellow-ish
			}
		}

		ActionResult.PASS
	}

	PlayerAdvancementCompletedCallback.EVENT.register { player, advancement, _, shouldAnnounceToChat ->
		if ( !shouldAnnounceToChat ) return@register ActionResult.PASS

		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		val advancementTitle = advancement.display?.title?.string
		val advancementText = advancement.display?.frame?.getText() ?: "gained the achievement"
		val advancementType = advancement.display?.frame?.getType()
		val advancementColor = advancement.display?.frame?.getColor()

		if ( isRelayChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } $advancementText ${ advancementTitle ?: "Unknown" }.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = advancementColor
			}
		}

		if ( isLogChannelConfigured ) DiscordRelay.coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Completed Advancement"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "Title", value = advancementTitle ?: "*Unknown*", inline = true ),
					EmbedField( name = "Type", value = advancementType ?: "*Unknown*", inline = true ),
					EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
					EmbedField( name = "Position", value = player.pos.toHumanReadableString(), inline = true ),
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

private fun AdvancementFrame?.getText(): String? =
	when ( this ) {
		AdvancementFrame.TASK -> "has made the advancement"
		AdvancementFrame.CHALLENGE -> "completed the challenge"
		AdvancementFrame.GOAL -> "reached the goal"
		else -> null
	}

private fun AdvancementFrame?.getType(): String? =
	when ( this ) {
		AdvancementFrame.TASK -> "Advancement"
		AdvancementFrame.CHALLENGE -> "Challenge"
		AdvancementFrame.GOAL -> "Goal"
		else -> null
	}

private fun AdvancementFrame?.getColor(): Int =
	when ( this ) {
		AdvancementFrame.CHALLENGE -> 0xA700A7 // Challenge Purple
		else -> 0x54FB54 // Advancement Green
	}

private fun Vec3d.toHumanReadableString(): String =
	arrayOf( this.getX(), this.getY(), this.getZ() )
		.map { it.roundToInt() }
		.joinToString( ", " )
