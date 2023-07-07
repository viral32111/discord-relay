package com.viral32111.discordrelay

import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.API
import com.viral32111.discordrelay.discord.data.*
import com.viral32111.discordrelay.helper.formatInUTC
import com.viral32111.discordrelay.helper.getCurrentDateTimeISO8601
import com.viral32111.discordrelay.helper.getCurrentDateTimeUTC
import com.viral32111.discordrelay.helper.toHumanReadableTime
import com.viral32111.events.callback.server.PlayerCompleteAdvancementCallback
import com.viral32111.events.callback.server.PlayerDeathCallback
import com.viral32111.events.callback.server.PlayerJoinCallback
import com.viral32111.events.callback.server.PlayerLeaveCallback
import com.viral32111.events.getColor
import com.viral32111.events.getText
import com.viral32111.events.getType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
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

fun registerCallbackListeners( coroutineScope: CoroutineScope, configuration: Configuration ) {
	val relayWebhookIdentifier = configuration.discord.channels.relay.webhook.identifier
	val relayWebhookToken = configuration.discord.channels.relay.webhook.token
	val isRelayChannelConfigured = relayWebhookIdentifier.isNotBlank() && relayWebhookToken.isNotBlank()

	val logWebhookIdentifier = configuration.discord.channels.log.webhook.identifier
	val logWebhookToken = configuration.discord.channels.log.webhook.token
	val isLogChannelConfigured = logWebhookIdentifier.isNotBlank() && logWebhookToken.isNotBlank()

	val statusCategoryIdentifier = configuration.discord.channels.status.identifier
	val statusCategoryName = configuration.discord.channels.status.name
	val isStatusCategoryConfigured = statusCategoryIdentifier.isNotBlank() && statusCategoryName.isNotBlank()

	val isProxyCheckConfigured = configuration.thirdParty.proxyCheck.api.key.isNotBlank()

	val avatarUrl = configuration.thirdParty.avatarUrl
	val profileUrl = configuration.thirdParty.profileUrl
	val serverUrl = configuration.thirdParty.serverUrl
	val ipAddressUrl = configuration.thirdParty.ipAddressUrl

	ServerLifecycleEvents.SERVER_STARTED.register { server ->
		serverStartTime = Instant.now()

		val serverPublicAddress = configuration.publicAddress.ifBlank { "${ server.serverIp }:${ server.serverPort }" }
		val serverPublicUrl = serverUrl.format( serverPublicAddress )

		coroutineScope.launch {
			if ( isRelayChannelConfigured ) API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor( "The server is now open!" )
				description = "Join at [`$serverPublicAddress`]($serverPublicUrl)."
				color = 0x00FF00 // Green
			}

			if ( isLogChannelConfigured ) {
				val serverIconFile = Path( server.runDirectory.absolutePath, configuration.serverIconFileName )

				val embedBuilder = EmbedBuilder().apply {
					title = "Server Started"
					fields = listOf(
						EmbedField( name = "IP Address", value = "[`$serverPublicAddress`]($serverPublicUrl)", inline = false ),
						EmbedField( name = "Version", value = server.version, inline = true ),
						EmbedField( name = "Whitelist", value = if ( server.playerManager.isWhitelistEnabled ) "Enabled" else "Disabled", inline = true ),
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

			if ( isStatusCategoryConfigured ) API.updateChannel( statusCategoryIdentifier ) {
				name = statusCategoryName.format( "Online" )
			}
		}
	}

	ServerLifecycleEvents.SERVER_STOPPING.register { server ->
		val serverUptime = ( Instant.now().epochSecond - serverStartTime.epochSecond ).toHumanReadableTime()

		coroutineScope.launch {
			if ( isRelayChannelConfigured ) API.sendWebhookEmbedWithoutWaiting( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor( "The server has closed." )
				description = "Open for $serverUptime."
				color = 0xFF0000 // Red
			}

			if ( isLogChannelConfigured ) {
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
					API.sendWebhookEmbedWithAttachmentWithoutWaiting( logWebhookIdentifier, logWebhookToken, serverIconFile, embedBuilder.build() )
				} else {
					API.sendWebhookEmbedWithoutWaiting( logWebhookIdentifier, logWebhookToken, embedBuilder.build() )
				}
			}

			if ( isStatusCategoryConfigured ) API.updateChannel( statusCategoryIdentifier ) {
				name = statusCategoryName.format( "Offline" )
			}
		}
	}

	ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
		if ( isRelayChannelConfigured ) coroutineScope.launch {
			API.sendWebhookText( relayWebhookIdentifier, relayWebhookToken ) {
				this.avatarUrl = avatarUrl.format( player.uuidAsString )
				userName = player.name.string
				content = message.content.string
			}
		}
	}

	PlayerJoinCallback.EVENT.register { connection, player ->
		val playerIpAddress = ( connection.address as InetSocketAddress ).address.hostAddress
		playerIPAddresses[ player.uuid ] = playerIpAddress

		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		coroutineScope.launch {
			val ipAddress = if ( isProxyCheckConfigured && !ProxyCheck.isPrivate( playerIpAddress ) ) ProxyCheck.check( playerIpAddress ) else null

			if ( isRelayChannelConfigured ) API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } joined${ if ( ipAddress?.countryName?.isNotBlank() == true ) " from ${ ipAddress.countryName }" else "" }.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFFF // White
			}

			if ( isLogChannelConfigured ) {
				API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
					title = "Player Joined"
					fields = listOf(
						EmbedField( name = "Name", value = player.name.string, inline = true ),
						EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
						EmbedField( name = "Operator", value = player.isOperator().toYesNo(), inline = true ),
						EmbedField( name = "Dimension", value = player.getDimensionName(), inline = true ),
						EmbedField( name = "Position", value = player.pos.toHumanReadableString(), inline = true ),
						EmbedField( name = "Location", value = if ( ipAddress != null ) "${ ipAddress.regionName?.ifBlank { "Unknown" } }, ${ ipAddress.countryName?.ifBlank { "Unknown" } } (${ ipAddress.continentName?.ifBlank { "Unknown" } })" else "*Unknown*", inline = false ),
						EmbedField( name = "IP Address", value = "[`$playerIpAddress`](${ ipAddressUrl.format( playerIpAddress ) })", inline = true ),
						EmbedField( name = "VPN", value = if ( ipAddress != null ) "${ ( ipAddress.isVPN || ipAddress.isProxy ).toYesNo() } (${ ipAddress.riskScore }% risk)" else "*Unknown*", inline = true ),
						EmbedField( name = "Unique Identifier", value = "[`${ player.uuidAsString }`]($playerProfileUrl)", inline = false )
					)
					thumbnail = EmbedThumbnail( url = playerAvatarUrl )
					footer = EmbedFooter( text = getCurrentDateTimeUTC( configuration.dateTimeFormat ) )
					timestamp = getCurrentDateTimeISO8601()
					color = 0xFFFFFF // White
				}
			}

			if ( isStatusCategoryConfigured ) API.updateChannel( statusCategoryIdentifier ) {
				name = statusCategoryName.format( "${ player.server.currentPlayerCount } playing" )
			}
		}

		ActionResult.PASS
	}

	PlayerLeaveCallback.EVENT.register { player ->
		val playerIPAddress = playerIPAddresses.getOrDefault( player.uuid, "*Unknown*" )
		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		coroutineScope.launch {
			if ( isRelayChannelConfigured ) API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } left.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFFF // White
			}

			if ( isLogChannelConfigured ) {
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

			if ( isStatusCategoryConfigured ) API.updateChannel( statusCategoryIdentifier ) {
				name = statusCategoryName.format( "${ player.server.currentPlayerCount } playing" )
			}
		}

		ActionResult.PASS
	}

	PlayerDeathCallback.EVENT.register { player, damageSource ->
		val deathMessage = "${ damageSource.getDeathMessage( player ).string }."

		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		if ( isRelayChannelConfigured ) coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = deathMessage,
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = 0xFFFFAA // Yellow-ish
			}
		}

		if ( isLogChannelConfigured ) coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Died"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "Score", value = player.score.toString(), inline = true ),
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

	PlayerCompleteAdvancementCallback.EVENT.register { player, advancement, _, shouldAnnounceToChat ->
		if ( !shouldAnnounceToChat ) return@register ActionResult.PASS

		val playerProfileUrl = profileUrl.format( player.uuidAsString )
		val playerAvatarUrl = avatarUrl.format( player.uuidAsString )

		val advancementTitle = advancement.display?.title?.string
		val advancementText = advancement.getText() ?: "gained the achievement"
		val advancementColor = advancement.getColor()

		if ( isRelayChannelConfigured ) coroutineScope.launch {
			API.sendWebhookEmbed( relayWebhookIdentifier, relayWebhookToken ) {
				author = EmbedAuthor(
					name = "${ player.name.string } $advancementText ${ advancementTitle ?: "Unknown" }.",
					url = playerProfileUrl,
					iconUrl = playerAvatarUrl
				)
				color = advancementColor
			}
		}

		if ( isLogChannelConfigured ) coroutineScope.launch {
			API.sendWebhookEmbed( logWebhookIdentifier, logWebhookToken ) {
				title = "Player Completed Advancement"
				fields = listOf(
					EmbedField( name = "Name", value = player.name.string, inline = true ),
					EmbedField( name = "Nickname", value = player.getNickName() ?: "*Not set*", inline = true ),
					EmbedField( name = "Title", value = advancementTitle ?: "*Unknown*", inline = true ),
					EmbedField( name = "Type", value = advancement.getType() ?: "*Unknown*", inline = true ),
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

private fun Vec3d.toHumanReadableString(): String =
	arrayOf( this.getX(), this.getY(), this.getZ() )
		.map { it.roundToInt() }
		.joinToString( ", " )

private fun Boolean.toString( truthy: String, falsy: String ): String = if ( this ) truthy else falsy
private fun Boolean.toYesNo(): String = this.toString( "Yes", "No" )
