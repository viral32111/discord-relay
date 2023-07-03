package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.WebSocketCloseCode
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Gateway
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.time.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.IOException
import java.net.URI
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.random.Random

class Gateway( private val configuration: Configuration ) {

	// Dedicated scope for our coroutines to run on
	private val coroutineScope = CoroutineScope( Dispatchers.IO )

	// The underlying WebSocket connection
	private var webSocket: WebSocket? = null

	// Future that completes when the underlying WebSocket receives a close event
	private var connectionClosureConfirmation: CompletableDeferred<Int>? = null

	// Job & completable for heartbeating in the background
	private var heartbeatJob: Job? = null
	private var heartbeatAcknowledgementConfirmation: CompletableDeferred<Unit>? = null

	// Last known sequence number for heartbeating & session resuming
	private var sequenceNumber: Int? = null

	//private var reconnectCount = 0

	/**
	 * Opens a WebSocket connection to the given URL, closing any existing connections beforehand.
	 * Ideally wait for closure confirmation after calling this.
	 * @param baseUrl The base URL of the WebSocket. Should not include any parameters.
	 * @param version The gateway version to include in the parameters.
	 * @return The underlying WebSocket.
	 * @see awaitClosure
	 */
	suspend fun open( baseUrl: String, version: Int = configuration.discord.api.version ) {
		val url = URI.create( "$baseUrl?v=$version&encoding=json" )

		if ( webSocket != null ) {
			DiscordRelay.LOGGER.debug( "Closing existing WebSocket connection..." )
			close( WebSocketCloseCode.GoingAway, "Closing existing connection." )
		}


		DiscordRelay.LOGGER.debug( "Opening new WebSocket connection to '$url'..." )
		connectionClosureConfirmation = CompletableDeferred()
		webSocket = HTTP.startWebSocketConnection( url, configuration.http.timeoutSeconds, Listener() )
	}

	/**
	 * Closes the underlying WebSocket connection.
	 * @param code The WebSocket close code. https://www.rfc-editor.org/rfc/rfc6455.html#section-7.4.1
	 * @param reason The human-readable reason for closing the connection.
	 */
	suspend fun close( code: Int = WebSocketCloseCode.Normal, reason: String = "Unknown." ) {
		try {
			DiscordRelay.LOGGER.debug( "Closing WebSocket connection with code $code & reason '$reason'..." )
			webSocket?.sendClose( code, reason )?.await()
		} catch ( exception: IOException ) {
			DiscordRelay.LOGGER.error( "Cannot close WebSocket connection! (${ exception.message })" )
		}

		DiscordRelay.LOGGER.debug( "Cancelling coroutines..." )
		coroutineScope.cancel()

		DiscordRelay.LOGGER.debug( "Confirming closure..." )
		connectionClosureConfirmation?.complete( code )
	}

	/**
	 * Waits for a confirmation of WebSocket closure.
	 * @return The WebSocket close code, if a connection was active.
	 */
	suspend fun awaitClosure(): Int? {
		DiscordRelay.LOGGER.debug( "Waiting for closure confirmation..." )
		val closeCode = connectionClosureConfirmation?.await()
		DiscordRelay.LOGGER.debug( "Closure confirmed with code $closeCode." )
		return closeCode
	}

	// Starts heartbeating in the background
	private fun startHeartbeating( webSocket: WebSocket, interval: Long ) {
		if ( heartbeatJob != null ) {
			DiscordRelay.LOGGER.debug( "Cancelling existing background heartbeating job..." )
			heartbeatJob?.cancel()
		}

		DiscordRelay.LOGGER.debug( "Starting new background heartbeating job..." )
		heartbeatJob = coroutineScope.launch {
			heartbeatLoop( webSocket, interval )
		}
	}

	// Sends heartbeats on an interval - https://discord.com/developers/docs/topics/gateway#sending-heartbeats
	private suspend fun heartbeatLoop( webSocket: WebSocket, regularInterval: Long ) {
		val initialInterval = ( regularInterval * Random.nextFloat() ).toLong()
		DiscordRelay.LOGGER.debug( "Waiting $initialInterval milliseconds for the initial heartbeat..." )
		delay( initialInterval )

		sendHeartbeat()

		while ( !webSocket.isOutputClosed ) {
			DiscordRelay.LOGGER.debug( "Waiting $regularInterval milliseconds for the next heartbeat..." )
			delay( regularInterval )

			sendHeartbeat()
		}
	}

	// Sends a heartbeat event to Discord - https://discord.com/developers/docs/topics/gateway-events#heartbeat
	private suspend fun sendHeartbeat() {
		heartbeatAcknowledgementConfirmation = CompletableDeferred()

		DiscordRelay.LOGGER.debug( "Sending heartbeat at sequence number $sequenceNumber..." )
		sendEvent( Gateway.Event.OperationCode.Heartbeat, JsonPrimitive( sequenceNumber ) )

		try {
			withTimeout( Duration.ofSeconds( configuration.discord.gateway.heartbeatTimeoutSeconds ) ) {
				DiscordRelay.LOGGER.debug( "Waiting for heartbeat acknowledgement..." )
				heartbeatAcknowledgementConfirmation?.await()
				DiscordRelay.LOGGER.debug( "Received heartbeat acknowledgement!" )
			}
		} catch ( exception: TimeoutCancellationException ) {
			DiscordRelay.LOGGER.warn( "Timed out while waiting for a heartbeat acknowledgement!" )

			DiscordRelay.LOGGER.debug( "Closing WebSocket connection..." )
			close( WebSocketCloseCode.ProtocolError, "No heartbeat acknowledgement." )
		}
	}

	// Sends an identify event to Discord - https://discord.com/developers/docs/topics/gateway#identifying
	private suspend fun sendIdentify() {
		DiscordRelay.LOGGER.debug( "Sending identification..." )
		sendEvent( Gateway.Event.OperationCode.Identify, JSON.encodeToJsonElement( Gateway.Event.Data.Identify(
			applicationToken = configuration.discord.application.token,
			intents = 1 shl 9, // Server messages - https://discord.com/developers/docs/topics/gateway#gateway-intents
			connectionProperties = Gateway.Event.Data.Identify.ConnectionProperties(
				operatingSystemName = "Minecraft Server",
				browserName = "viral32111's discord relay",
				deviceName = "viral32111's discord relay"
			)
		) ) )
	}

	// Sends an event to Discord - https://discord.com/developers/docs/topics/gateway-events#send-events
	private suspend fun sendEvent( operationCode: Int, data: JsonElement? ) {
		val jsonPayload = JSON.encodeToString( Gateway.Event(
			operationCode = operationCode,
			data = data
		) )

		DiscordRelay.LOGGER.debug( "Sending JSON payload '$jsonPayload' over WebSocket..." )
		webSocket?.sendText( jsonPayload, true )?.await()
	}

	// https://discord.com/developers/docs/topics/gateway-events#hello
	private fun handleHelloEvent( webSocket: WebSocket, data: JsonElement? ) {
		if ( data == null ) throw IllegalStateException( "Received Gateway Hello event without data" )

		val heartbeatInterval = JSON.decodeFromJsonElement<Gateway.Event.Data.Hello>( data ).heartbeatInterval
		startHeartbeating( webSocket, heartbeatInterval )

		coroutineScope.launch {
			sendIdentify()
		}
	}

	// https://discord.com/developers/docs/topics/gateway-events#ready
	private fun handleReadyEvent( webSocket: WebSocket, data: JsonElement? ) {
		if ( data == null ) throw IllegalStateException( "Received Gateway Ready event without data" )

		DiscordRelay.LOGGER.debug( "We're ready." )

		// TODO: Use event data
	}

	// https://discord.com/developers/docs/topics/gateway-events#reconnect
	private fun handleReconnectEvent() {
		DiscordRelay.LOGGER.debug( "We need to reconnect!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Told to reconnect." )
			// TODO: reconnect()
		}
	}

	private fun handleInvalidSessionEvent() {
		DiscordRelay.LOGGER.warn( "Our session is invalid!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Session is invalid." )
			// TODO: reconnect()
		}
	}

	private fun handleHeartbeat() {
		DiscordRelay.LOGGER.debug( "Heartbeat requested." )

		coroutineScope.launch {
			sendHeartbeat()
		}
	}

	private fun handleHeartbeatAcknowledgement() {
		DiscordRelay.LOGGER.debug( "Heartbeat acknowledged." )
		heartbeatAcknowledgementConfirmation?.complete( Unit )
	}

	private fun handleEvent( webSocket: WebSocket, message: String ) {
		val event = JSON.decodeFromString<Gateway.Event>( message )
		DiscordRelay.LOGGER.debug( "Received JSON payload '$message' from the WebSocket." )

		if ( event.sequenceNumber != null ) {
			DiscordRelay.LOGGER.debug( "Incremented sequence number from $sequenceNumber to ${ event.sequenceNumber }." )
			sequenceNumber = event.sequenceNumber
		}

		when ( event.operationCode ) {
			Gateway.Event.OperationCode.Ready -> handleReadyEvent( webSocket, event.data )
			Gateway.Event.OperationCode.Hello -> handleHelloEvent( webSocket, event.data )
			Gateway.Event.OperationCode.Reconnect -> handleReconnectEvent()
			Gateway.Event.OperationCode.InvalidSession -> handleInvalidSessionEvent()
			Gateway.Event.OperationCode.Heartbeat -> handleHeartbeat()
			Gateway.Event.OperationCode.HeartbeatAcknowledgement -> handleHeartbeatAcknowledgement()

			else -> DiscordRelay.LOGGER.warn( "Ignoring Gateway event ${ event.operationCode } (${ event.name }: ${ JSON.encodeToString( event.data ) })." )
		}
	}

	/*
	private suspend fun reconnect() {
		val ms = ( 2.0.pow( reconnectCount ) * 1000 ).toLong()
		DiscordRelay.LOGGER.debug( "waiting $ms ms before reconnecting" )
		delay( ms )

		DiscordRelay.LOGGER.debug( "trying reconnect..." )
		open()

		reconnectCount++
	}
	*/

	private inner class Listener: WebSocket.Listener {
		private val messageBuilder = StringBuilder()

		override fun onOpen( webSocket: WebSocket ) {
			DiscordRelay.LOGGER.debug( "WebSocket connection opened." )
			webSocket.request( 1 )
		}

		override fun onClose( webSocket: WebSocket, code: Int, reason: String? ): CompletionStage<*> {
			DiscordRelay.LOGGER.debug( "WebSocket connection closed with code $code & reason '$reason'." )

			// TODO: check code against https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-close-event-codes
			// TODO: don't auto-reconnect if we requested the close
			// TODO: coroutineScope.launch { reconnect() }

			return CompletableFuture.completedFuture( null )
		}

		override fun onText( webSocket: WebSocket, data: CharSequence?, isLastMessage: Boolean ): CompletionStage<*>? {
			DiscordRelay.LOGGER.debug( "Received text chunk of ${ data?.length } character(s)." )

			messageBuilder.append( data )

			if ( isLastMessage ) {
				handleEvent( webSocket, messageBuilder.toString() )
				messageBuilder.clear()
			}

			webSocket.request( 1 )

			return CompletableFuture.completedFuture( null )
		}

		override fun onError( webSocket: WebSocket, error: Throwable? ) {
			DiscordRelay.LOGGER.error( "WebSocket error: '$error'" )

			// TODO: coroutineScope.launch { reconnect() }
		}
	}
}
