package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.GatewayEventPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.encodeToString
import java.net.URI
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

class Gateway( private val webSocketUrl: String, private val configuration: Configuration ) {
	private var webSocket: WebSocket? = null
	private val isOpen = AtomicBoolean( false )
	private val coroutineScope = CoroutineScope( Dispatchers.IO )
	private var reconnectCount = 0

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

	/*suspend private fun heartbeat() {
		throw NotImplementedError()
	}*/

	private fun processMessage( text: String ) {
		val webSocket = webSocket ?: throw IllegalStateException( "Gateway not initialized" )

		val payload = JSON.decodeFromString<GatewayEventPayload>( text )
		DiscordRelay.LOGGER.info( "${ payload.operationCode }, ${ payload.sequenceNumber }, ${ payload.eventName }: '${ JSON.encodeToString( payload.eventData ) }'" )

		when ( payload.operationCode ) {
			0 -> { // event dispatch
				// todo
			}

			10 -> { // hello
				// TODO: start heartbeating
				// TODO: send identify
			}

			7 -> { // reconnect
				DiscordRelay.LOGGER.info( "discord told us to reconnect" )
				webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "See you soon." )
				coroutineScope.launch { reconnect() }
			}

			9 -> { // invalid session
				DiscordRelay.LOGGER.info( "gg our session is invalid" )
				webSocket.sendClose( WebSocket.NORMAL_CLOSURE, "See you soon." )
				coroutineScope.launch { reconnect() }
			}
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

				DiscordRelay.LOGGER.info( "received text: '${ text }'" )

				processMessage( text )
			}

			webSocket.request( 1 )

			return null
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
