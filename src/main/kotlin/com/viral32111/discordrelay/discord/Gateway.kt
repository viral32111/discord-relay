package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.GatewayEventPayload
import com.viral32111.discordrelay.discord.data.GatewayHello
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.random.Random

class Gateway( private val webSocketUrl: String, private val configuration: Configuration ) {
	private var webSocket: WebSocket? = null
	private val isOpen = AtomicBoolean( false )
	private val coroutineScope = CoroutineScope( Dispatchers.IO )
	private var reconnectCount = 0
	private var sequenceNumber: Int? = null

	private enum class OperationCodes( val code: Int ) {
		Hello( 10 ),
		Ready( 0 ),
		Resume( 6 ),
		Reconnect( 7 ),
		InvalidSession( 9 ),
		Heartbeat( 1 ),
		HeartbeatAcknowledgement( 11 ),
		Identify( 2 )
	}

	suspend fun open() {
		if ( isOpen.get() ) throw IllegalStateException( "Gateway already open" )

		DiscordRelay.LOGGER.info( "opening" )

		webSocket = HTTP.createWebSocket( URI.create( "$webSocketUrl?v=${ configuration.discord.api.version }&encoding=json" ), Listener(), configuration )
	}

	suspend fun close() {
		val webSocket = webSocket ?: throw IllegalStateException( "Gateway not initialized" )
		if ( !isOpen.get() ) throw IllegalStateException( "Gateway not open" )

		DiscordRelay.LOGGER.info( "closing" )

		webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "Goodbye." ).await()

		isOpen.set( false )

		coroutineScope.cancel()
	}

	// https://discord.com/developers/docs/topics/gateway#sending-heartbeats
	private var heartbeatJob: Job? = null
	private fun startHeartbeating( webSocket: WebSocket, interval: Long ) {
		heartbeatJob?.cancel()

		var isFirstHeartbeat = true

		heartbeatJob = coroutineScope.launch {
			while ( isOpen.get() ) {
				if ( isFirstHeartbeat ) {
					val firstInterval = ( interval * Random.nextFloat() ).toLong()
					DiscordRelay.LOGGER.info( "waiting $firstInterval ms until first heartbeat..." )
					delay( firstInterval )
					isFirstHeartbeat = false
				} else {
					DiscordRelay.LOGGER.info( "waiting $interval ms until next heartbeat..." )
					delay( interval )
				}

				// todo: data class for heartbeat
				val heartbeatJsonPayload = JSON.encodeToString( buildJsonObject {
					put( "op", OperationCodes.Heartbeat.code )
					put( "d", sequenceNumber )
				} )
				DiscordRelay.LOGGER.info( "heartbeating!!! ($sequenceNumber): '$heartbeatJsonPayload'" )
				webSocket.sendText( heartbeatJsonPayload, true ).await()
				DiscordRelay.LOGGER.info( "did heartbeat (wait for ack now)" )
			}
		}
	}

	private fun processMessage( webSocket: WebSocket, text: String ) {
		val payload = JSON.decodeFromString<GatewayEventPayload>( text )
		DiscordRelay.LOGGER.info( "${ payload.operationCode }, ${ payload.sequenceNumber }, ${ payload.eventName }: '${ JSON.encodeToString( payload.eventData ) }'" )

		if ( payload.sequenceNumber != null ) {
			sequenceNumber = payload.sequenceNumber
			DiscordRelay.LOGGER.info( "new seq number $sequenceNumber" )
		}

		when ( payload.operationCode ) {
			OperationCodes.Ready.code -> {
				DiscordRelay.LOGGER.info( "we is ready" )
				// todo
			}

			OperationCodes.Hello.code -> {
				if ( payload.eventData == null ) throw java.lang.IllegalStateException( "Received initial hello/opcode 10 payload without data" )

				val heartbeatInterval = JSON.decodeFromJsonElement<GatewayHello>( payload.eventData ).heartbeatInterval
				DiscordRelay.LOGGER.info( "hello there, imma heartbeat every $heartbeatInterval ms" )
				startHeartbeating( webSocket, heartbeatInterval )

				// https://discord.com/developers/docs/topics/gateway#identifying
				// https://discord.com/developers/docs/topics/gateway-events#identify-identify-structure
				DiscordRelay.LOGGER.info( "i should identify..." )
				// todo: data class for identify
				val identifyJsonPayload = JSON.encodeToString( buildJsonObject {
					put( "op", OperationCodes.Identify.code )
					putJsonObject( "d" ) {
						put( "token", configuration.discord.application.token )
						putJsonObject( "properties" ) {
							put( "os", "Minecraft Server" ) // todo: use user agent prefix
							put( "browser", "viral32111's discord relay" )
							put( "device", "viral32111's discord relay" )
						}
						put( "compress", false )
						put( "intents", 1 shl 9 ) // guild messages - https://discord.com/developers/docs/topics/gateway#gateway-intents
					}
				} )
				DiscordRelay.LOGGER.info( "identifying!!! ($sequenceNumber): '$identifyJsonPayload'" )
				coroutineScope.launch {
					webSocket.sendText(identifyJsonPayload, true).await()
				}
			}

			OperationCodes.Reconnect.code -> {
				DiscordRelay.LOGGER.info( "discord told us to reconnect" )
				webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "See you soon." )
				coroutineScope.launch { reconnect() }
			}

			OperationCodes.InvalidSession.code -> {
				DiscordRelay.LOGGER.info( "gg our session is invalid" )
				webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "See you soon." )
				coroutineScope.launch { reconnect() }
			}

			OperationCodes.Heartbeat.code -> {
				DiscordRelay.LOGGER.info( "discord begs for a heartbeat" )
				// todo
			}

			OperationCodes.HeartbeatAcknowledgement.code -> {
				DiscordRelay.LOGGER.info( "discord loves our heartbeat" )
				// todo
			}

			else -> DiscordRelay.LOGGER.info( "ignoring operation code ${ payload.operationCode }" )
		}
	}

	private suspend fun reconnect() {
		val ms = ( 2.0.pow( reconnectCount ) * 1000 ).toLong()
		DiscordRelay.LOGGER.info( "waiting $ms ms before reconnecting" )
		delay( ms )

		DiscordRelay.LOGGER.info( "trying reconnect..." )
		open()

		reconnectCount++
	}

	private inner class Listener: WebSocket.Listener {
		private val textBuilder = StringBuilder()

		override fun onOpen( webSocket: WebSocket ) {
			DiscordRelay.LOGGER.info( "opened" )

			isOpen.set( true )

			webSocket.request( 1 )
		}

		override fun onClose( webSocket: WebSocket, statusCode: Int, reason: String? ): CompletionStage<*> {
			DiscordRelay.LOGGER.info( "closed: $statusCode ($reason)" )

			isOpen.set( false )

			// todo: dont reconnect if we requested the close
			DiscordRelay.LOGGER.info( "we need to reconnect" )
			coroutineScope.launch { reconnect() }

			return CompletableFuture.completedFuture( null )
		}

		override fun onText( webSocket: WebSocket, data: CharSequence?, last: Boolean ): CompletionStage<*>? {
			DiscordRelay.LOGGER.info( "received text chunk" )

			textBuilder.append( data )

			if ( last ) {
				val text = textBuilder.toString()
				textBuilder.clear()

				DiscordRelay.LOGGER.info( "received text: '$text'" )

				processMessage( webSocket, text )
			}

			webSocket.request( 1 )

			return CompletableFuture.completedFuture( null )
		}

		override fun onError( webSocket: WebSocket, error: Throwable? ) {
			DiscordRelay.LOGGER.error( "Gateway error: '${ error }'" )

			// todo: are we closed?
			isOpen.set( false )

			DiscordRelay.LOGGER.info( "should probably reconnect" )
			coroutineScope.launch { reconnect() }
		}
	}
}
