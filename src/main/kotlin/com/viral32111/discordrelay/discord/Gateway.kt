package com.viral32111.discordrelay.discord

import com.viral32111.discordrelay.DiscordRelay
import com.viral32111.discordrelay.HTTP
import com.viral32111.discordrelay.JSON
import com.viral32111.discordrelay.WebSocketCloseCode
import com.viral32111.discordrelay.config.Configuration
import com.viral32111.discordrelay.discord.data.Gateway
import com.viral32111.discordrelay.discord.data.Guild
import com.viral32111.discordrelay.helper.Version
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.time.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.minecraft.server.PlayerManager
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import net.minecraft.util.Formatting
import java.io.IOException
import java.net.URI
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.math.pow
import kotlin.random.Random

class Gateway( private val configuration: Configuration, private val playerManager: PlayerManager ) {

	// Dedicated scope for our coroutines to run on
	private val coroutineScope = CoroutineScope( Dispatchers.IO )

	// The underlying WebSocket connection
	private var webSocket: WebSocket? = null

	// Future that completes when the underlying WebSocket receives a close event
	private var connectionClosureConfirmation: CompletableDeferred<ClosureConfirmation>? = null

	// Job & completable for heartbeating in the background
	private var heartbeatJob: Job? = null
	private var heartbeatAcknowledgementConfirmation: CompletableDeferred<Unit>? = null

	// Last known sequence number for heartbeating & session resuming
	private var sequenceNumber: Int? = null

	// Used for session resuming
	private var sessionIdentifier: String? = null
	private var resumeBaseUrl: String? = null
	private var shouldResume = false

	// Number of times we've reconnected
	private var reconnectCount = 0

	// The bot ID & server roles, set during ready process
	private var myIdentifier: String? = null
	private var serverRoles: Map<String, Guild.Role>? = null

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
	suspend fun close( code: Int = WebSocketCloseCode.Normal, reason: String = "Unknown.", isServerStopping: Boolean = false ) {
		try {
			DiscordRelay.LOGGER.debug( "Closing WebSocket connection with code $code & reason '$reason' (Server Stopping: $isServerStopping)..." )
			webSocket?.sendClose( code, reason )?.await()
		} catch ( exception: IOException ) {
			DiscordRelay.LOGGER.error( "Cannot close WebSocket connection! (${ exception.message })" )
		}

		DiscordRelay.LOGGER.debug( "Confirming closure..." )
		connectionClosureConfirmation?.complete( ClosureConfirmation( code, reason, isServerStopping ) )
	}

	/**
	 * Waits for a confirmation of WebSocket closure.
	 * @return The WebSocket close code, if a connection was active.
	 */
	suspend fun awaitClosure(): ClosureConfirmation? {
		DiscordRelay.LOGGER.debug( "Waiting for closure confirmation..." )
		val confirmation = connectionClosureConfirmation?.await()
		DiscordRelay.LOGGER.debug( "Closure confirmed with code ${ confirmation?.closeCode }." )
		return confirmation
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

		sendHeartbeat( webSocket, this.sequenceNumber )

		while ( !webSocket.isOutputClosed ) {
			DiscordRelay.LOGGER.debug( "Waiting $regularInterval milliseconds for the next heartbeat..." )
			delay( regularInterval )

			sendHeartbeat( webSocket, this.sequenceNumber )
		}
	}

	// Sends a heartbeat event to Discord - https://discord.com/developers/docs/topics/gateway-events#heartbeat
	private suspend fun sendHeartbeat( webSocket: WebSocket, sequenceNumber: Int? ) {
		heartbeatAcknowledgementConfirmation = CompletableDeferred()

		DiscordRelay.LOGGER.debug( "Sending heartbeat at sequence number $sequenceNumber..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Heartbeat, JsonPrimitive( sequenceNumber ) )

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
	private suspend fun sendIdentify( webSocket: WebSocket ) {
		val libraryName = "viral32111's discord relay/${ Version.discordRelay() } (https://github.com/viral32111/discord-relay)"

		// Just to be sure
		this.sessionIdentifier = null
		this.resumeBaseUrl = null
		DiscordRelay.LOGGER.debug( "Reset session identifier & resume base URL." )

		DiscordRelay.LOGGER.debug( "Sending identify..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Identify, JSON.encodeToJsonElement( Gateway.Event.Data.Identify(
			applicationToken = configuration.discord.application.token,
			intents = Gateway.Event.Data.Identify.Intents.Guilds or
					Gateway.Event.Data.Identify.Intents.GuildMessages or
					Gateway.Event.Data.Identify.Intents.MessageContent,
			connectionProperties = Gateway.Event.Data.Identify.ConnectionProperties(
				operatingSystemName = configuration.http.userAgentPrefix,
				browserName = libraryName,
				deviceName = libraryName
			)
		) ) )
	}

	// Sends a resume event to Discord - https://discord.com/developers/docs/topics/gateway#resuming
	private suspend fun sendResume( webSocket: WebSocket, sessionIdentifier: String, sequenceNumber: Int ) {
		DiscordRelay.LOGGER.debug( "Sending resume for session '$sessionIdentifier'..." )
		sendEvent( webSocket, Gateway.Event.OperationCode.Resume, JSON.encodeToJsonElement( Gateway.Event.Data.Resume(
			applicationToken = configuration.discord.application.token,
			sessionIdentifier = sessionIdentifier,
			sequenceNumber = sequenceNumber
		) ) )
	}

	// Sends an event to Discord - https://discord.com/developers/docs/topics/gateway-events#send-events
	private suspend fun sendEvent( webSocket: WebSocket, operationCode: Int, data: JsonElement? ) {
		if ( webSocket.isOutputClosed ) {
			DiscordRelay.LOGGER.warn( "WebSocket output closed when attempting to send data?!" )
			close( WebSocketCloseCode.GoingAway, "WebSocket output closed." )
			return
		}

		val jsonPayload = JSON.encodeToString( Gateway.Event(
			operationCode = operationCode,
			data = data
		) )

		DiscordRelay.LOGGER.debug( "Sending JSON payload '$jsonPayload' over WebSocket..." )
		webSocket.sendText( jsonPayload, true )?.await()
	}

	// https://discord.com/developers/docs/topics/gateway-events#hello
	private fun handleHelloEvent( webSocket: WebSocket, data: Gateway.Event.Data.Hello ) {
		val sessionIdentifier = this.sessionIdentifier
		val sequenceNumber = this.sequenceNumber

		startHeartbeating( webSocket, data.heartbeatInterval )

		coroutineScope.launch {
			if ( shouldResume && sessionIdentifier != null && sequenceNumber != null ) {
				sendResume( webSocket, sessionIdentifier, sequenceNumber )
				shouldResume = false
			} else {
				sendIdentify( webSocket )
			}
		}
	}

	// https://discord.com/developers/docs/topics/gateway-events#reconnect
	private fun handleReconnectEvent() {
		DiscordRelay.LOGGER.debug( "We need to reconnect!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Told to reconnect." )

			DiscordRelay.LOGGER.debug( "Reconnecting as instructed..." )
			tryReconnect( true )
		}
	}

	private fun handleInvalidSessionEvent( shouldResume: Boolean ) {
		DiscordRelay.LOGGER.warn( "Our session is invalid!" )

		coroutineScope.launch {
			close( WebSocketCloseCode.GoingAway, "Session is invalid." )

			DiscordRelay.LOGGER.debug( "Reconnecting due to invalid session..." )
			tryReconnect( shouldResume )
		}
	}

	private fun handleHeartbeat( webSocket: WebSocket ) {
		val sequenceNumber = this.sequenceNumber
		DiscordRelay.LOGGER.debug( "Heartbeat requested at sequence number '$sequenceNumber'." )

		coroutineScope.launch {
			sendHeartbeat( webSocket, sequenceNumber )
		}
	}

	private fun handleHeartbeatAcknowledgement() {
		DiscordRelay.LOGGER.debug( "Heartbeat acknowledged." )
		heartbeatAcknowledgementConfirmation?.complete( Unit )
	}

	// https://discord.com/developers/docs/topics/gateway-events#ready
	private fun handleReadyEvent( data: Gateway.Event.Data.Ready ) {
		DiscordRelay.LOGGER.info( "Ready as '${ data.user.name }#${ data.user.discriminator }' / '@${ data.user.name }' (${ data.user.identifier })." )
		myIdentifier = data.user.identifier

		sessionIdentifier = data.sessionIdentifier
		resumeBaseUrl = data.resumeUrl
		DiscordRelay.LOGGER.debug( "Set session identifier to '$sessionIdentifier' & resume base URL to '$resumeBaseUrl'." )
	}

	// https://discord.com/developers/docs/topics/gateway-events#message-create
	private fun handleMessageCreate( message: Gateway.Event.Data.MessageCreate ) {
		DiscordRelay.LOGGER.debug( "Received message '${ message.content }' (${ message.identifier }) in channel ${ message.channelIdentifier } from '@${ message.author.name }' (${ message.author.identifier })." )

		if ( message.channelIdentifier != configuration.discord.channels.relay.identifier ) {
			DiscordRelay.LOGGER.debug( "Ignoring non-relay channel message (${ message.identifier }) from '@${ message.author.name }' (${ message.author.identifier })." )
			return
		}

		if ( message.content.isBlank() ) {
			DiscordRelay.LOGGER.debug( "Ignoring empty message (${ message.identifier }) from '@${ message.author.name }' (${ message.author.identifier })." )
			return
		}

		if ( message.author.isBot == true || message.author.isSystem == true ) {
			DiscordRelay.LOGGER.debug( "Ignoring bot/system message '${ message.content }' (${ message.identifier }) from '@${ message.author.name }' (${ message.author.identifier })." )
			return
		}

		if ( message.author.identifier == myIdentifier ) {
			DiscordRelay.LOGGER.debug( "Ignoring my message '${ message.content }' (${ message.identifier }) from '@${ message.author.name }' (${ message.author.identifier })." )
			return
		}

		DiscordRelay.LOGGER.info( "Relaying Discord message '${ message.content }' (${ message.identifier}) from '@${ message.author.name }' (${ message.author.identifier })..." )

		val memberRoleColor = getMemberRoleColor( message.member )
		val playerStyle = getStyleOrDefault( memberRoleColor )
		DiscordRelay.LOGGER.debug( "Member role color is '$memberRoleColor' & player style is '${ playerStyle.color?.rgb }'" )

		val chatMessage: Text = Text.literal( "" )
			.append( Text.literal( "(Discord) " ).setStyle( Style.EMPTY.withColor( TextColor.fromFormatting( Formatting.BLUE ) ) )
			.append( Text.literal( message.member?.displayName ?: message.author.name ).setStyle( playerStyle ) ) )
			.append( Text.literal( ": " ) )
			.append( Text.literal( message.content ) )

		playerManager.broadcast( chatMessage, false )
	}

	// https://discord.com/developers/docs/topics/gateway-events#guild-create
	private fun handleGuildCreate( guild: Gateway.Event.Data.GuildCreate ) {
		if ( guild.identifier != configuration.discord.server.identifier ) {
			DiscordRelay.LOGGER.debug( "Ignoring guild create event for guild '${ guild.name }' (${ guild.identifier })." )
			return
		}

		serverRoles = guild.roles.associateBy { it.identifier }
	}

	private fun getMemberRoleColor( member: Guild.Member? ): Int? = member?.roleIdentifiers
		?.map { serverRoles?.get( it ) ?: return null }
		?.maxByOrNull { it.position }
		?.color

	private fun getStyleOrDefault( rgb: Int?, formatting: Formatting = Formatting.GREEN ): Style =
		if ( rgb != null ) Style.EMPTY.withColor( rgb ) else Style.EMPTY.withColor( TextColor.fromFormatting( formatting ) )

	private fun processMessage( webSocket: WebSocket, message: String ) {
		val event = JSON.decodeFromString<Gateway.Event>( message )
		DiscordRelay.LOGGER.debug( "Received JSON payload '$message' from the WebSocket." )

		if ( event.sequenceNumber != null ) {
			DiscordRelay.LOGGER.debug( "Incremented sequence number from $sequenceNumber to ${ event.sequenceNumber }." )
			sequenceNumber = event.sequenceNumber
		}

		when ( event.operationCode ) {
			Gateway.Event.OperationCode.Hello -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway hello operation '${ event.name }' without data" )
				handleHelloEvent( webSocket, JSON.decodeFromJsonElement<Gateway.Event.Data.Hello>( event.data ) )
			}
			Gateway.Event.OperationCode.Reconnect -> handleReconnectEvent()
			Gateway.Event.OperationCode.InvalidSession -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway invalid session operation '${ event.name }' without data" )
				handleInvalidSessionEvent( JSON.decodeFromJsonElement<Boolean>( event.data ) )
			}
			Gateway.Event.OperationCode.Heartbeat -> handleHeartbeat( webSocket )
			Gateway.Event.OperationCode.HeartbeatAcknowledgement -> handleHeartbeatAcknowledgement()

			// https://discord.com/developers/docs/topics/gateway-events#receive-events
			Gateway.Event.OperationCode.Dispatch -> {
				if ( event.data == null ) throw IllegalStateException( "Received Gateway event '${ event.name }' without data" )

				when ( event.name ) {
					Gateway.Event.Name.Ready -> handleReadyEvent( JSON.decodeFromJsonElement<Gateway.Event.Data.Ready>( event.data ) )
					Gateway.Event.Name.MessageCreate -> handleMessageCreate( JSON.decodeFromJsonElement<Gateway.Event.Data.MessageCreate>( event.data ) )
					Gateway.Event.Name.GuildCreate -> handleGuildCreate( JSON.decodeFromJsonElement<Gateway.Event.Data.GuildCreate>( event.data ) )

					else -> DiscordRelay.LOGGER.debug( "Ignoring Gateway event '${ event.name }' with data '${ JSON.encodeToString( event.data ) }'." )
				}
			}

			else -> DiscordRelay.LOGGER.debug( "Ignoring Gateway operation ${ event.operationCode } with data '${ JSON.encodeToString( event.data ) }'." )
		}
	}

	private suspend fun tryReconnect( shouldResume: Boolean ) {
		val duration = ( 2.0.pow( reconnectCount ) * 1000 ).toLong()
		DiscordRelay.LOGGER.debug( "Connection attempt $reconnectCount, waiting $duration milliseconds before reconnecting..." )
		delay( duration )

		val resumeBaseUrl = resumeBaseUrl
		val baseUrl = if ( shouldResume && resumeBaseUrl != null ) resumeBaseUrl else API.getGateway().url
		DiscordRelay.LOGGER.debug( "Trying reconnect to '$baseUrl' (Resume: $shouldResume)..." )
		this.shouldResume = shouldResume
		open( baseUrl )

		reconnectCount++
		DiscordRelay.LOGGER.debug( "Incremented reconnection count to $reconnectCount." )
	}

	private fun cleanupAfterError() {
		DiscordRelay.LOGGER.debug( "Resetting state due to WebSocket error..." )

		this.webSocket = null

		this.sessionIdentifier = null
		this.resumeBaseUrl = null
		this.shouldResume = false

		this.connectionClosureConfirmation?.cancel()
		this.connectionClosureConfirmation = null

		this.heartbeatAcknowledgementConfirmation?.cancel()
		this.heartbeatAcknowledgementConfirmation = null

		this.heartbeatJob?.cancel()
		this.heartbeatJob = null

		this.myIdentifier = null
	}

	private inner class Listener: WebSocket.Listener {
		private val messageBuilder = StringBuilder()

		override fun onOpen( webSocket: WebSocket ) {
			DiscordRelay.LOGGER.debug( "WebSocket connection opened." )
			webSocket.request( 1 )
		}

		override fun onClose( webSocket: WebSocket, code: Int, reason: String? ): CompletionStage<*> {
			DiscordRelay.LOGGER.debug( "WebSocket connection closed with code $code & reason '$reason'." )

			DiscordRelay.LOGGER.debug( "Cancelling background heartbeating job..." )
			heartbeatJob?.cancel()

			DiscordRelay.LOGGER.debug( "Cancelling gateway coroutines..." )
			coroutineScope.cancel()

			// TODO: Check code against https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-close-event-codes
			if ( code == WebSocketCloseCode.GoingAway || code == WebSocketCloseCode.ProtocolError ) {
				DiscordRelay.LOGGER.debug( "Reconnecting due to non-1000 close code..." )
				coroutineScope.launch {
					tryReconnect( false )
				}
			}

			return CompletableFuture.completedFuture( null )
		}

		override fun onText( webSocket: WebSocket, data: CharSequence?, isLastMessage: Boolean ): CompletionStage<*>? {
			DiscordRelay.LOGGER.debug( "Received text chunk of ${ data?.length } character(s)." )

			messageBuilder.append( data )

			if ( isLastMessage ) {
				processMessage( webSocket, messageBuilder.toString() )
				messageBuilder.clear()
			}

			webSocket.request( 1 )

			return CompletableFuture.completedFuture( null )
		}

		override fun onError( webSocket: WebSocket, error: Throwable? ) {
			DiscordRelay.LOGGER.error( "WebSocket error: '$error'" )

			// We're in a disconnected state but none of the closing code has run, so reset everything manually
			cleanupAfterError()

			DiscordRelay.LOGGER.debug( "Reconnecting due to WebSocket error..." )
			coroutineScope.launch {
				tryReconnect( false )
			}
		}
	}

	data class ClosureConfirmation(
		val closeCode: Int,
		val closeReason: String? = null,
		val isServerStopping: Boolean = false
	)
}
