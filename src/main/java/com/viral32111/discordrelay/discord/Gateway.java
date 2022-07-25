package com.viral32111.discordrelay.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;
import com.viral32111.discordrelay.discord.types.OperationCode;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;

// Client for the Discord Gateway
public class Gateway implements WebSocket.Listener {

	// The underlying websocket connection, used for stopping
	private static WebSocket webSocket = null;

	// Future that completes when the websocket connection closes, used for reconnecting depending on the boolean
	private static CompletableFuture<Boolean> connectionClosedFuture = null;

	// Compression should not be used, it is not implemented in this class
	private static final boolean USE_COMPRESSION = false;

	// Holds a list of  received message fragments for forming the final message
	private final ArrayList<CharSequence> messageFragments = new ArrayList<>();

	// The current payload sequence number
	private Integer sequenceNumber = null;

	// The futures for heartbeating
	private CompletableFuture<?> heartbeatFuture = null;
	private CompletableFuture<?> heartbeatAcknowledgementFuture = null;

	// Starts the initial connection to the gateway
	public static void Start() {

		// Fetch the gateway URL
		API.Request( "GET", "gateway/bot", null, null ).thenAccept( ( JsonObject response ) -> {

			// Add connection properties to the returned URL
			URI url = URI.create( String.format( "%s/?v=%s&encoding=json&compress=%b", response.get( "url" ).getAsString(), Config.Get( "discord.api.version", null ), USE_COMPRESSION ) );

			// Begin the first connection to the above URL
			Connect( url, new Gateway() );

		} );

	}

	public static void Stop() throws Exception {

		if ( webSocket == null ) throw new Exception( "Underlying websocket connection not initialised" );

		webSocket.sendClose( 1000, "Goodbye" );

	}

	// Connects to a websocket server, and reconnects whenever the close future completes
	private static void Connect( URI url, Gateway listener ) {

		// Create the future for closing
		connectionClosedFuture = new CompletableFuture<>();

		// Asynchronously connect to the gateway, using the provided URL and options from the configuration
		CompletableFuture<WebSocket> webSocketConnectFuture = Utilities.HTTP_CLIENT.newWebSocketBuilder()

			// Additional headers in the upgrade request
			.header( "User-Agent", Config.Get( "http.user-agent", null ) )
			.header( "From", Config.Get( "http.from", null ) )

			// Upgrade request timeout to the server
			.connectTimeout( Duration.ofSeconds( Config.Get( "http.timeout" ) )  )

			// URL and listener instance of this class
			.buildAsync( url, listener );

		// Update the websocket property with the new websocket instance
		webSocketConnectFuture.thenAccept( ( ws ) -> webSocket = ws );

		// Reconnect to the same URL and with the same listener instance whenever the future completes
		connectionClosedFuture.thenAccept( ( shouldReconnect ) -> {
			if ( shouldReconnect ) Connect( url, listener );
		} );

	}

	// Check if the websocket is currently open
	private boolean IsConnected( WebSocket webSocket ) {
		return !( webSocket.isInputClosed() || webSocket.isOutputClosed() );
	}

	private void SendHeartbeat( WebSocket webSocket ) {

		heartbeatAcknowledgementFuture = new CompletableFuture<>();

		try {
			JsonObject heartbeatPayload = new JsonObject();
			heartbeatPayload.addProperty( "op", OperationCode.Heartbeat );
			heartbeatPayload.addProperty( "d", this.sequenceNumber );

			webSocket.sendText( heartbeatPayload.toString(), true );

		} catch ( Exception exception ) {
			Utilities.Error( exception );
			webSocket.sendClose( 1002, "Internal error" );
		}

		try {
			heartbeatAcknowledgementFuture.orTimeout( 5, TimeUnit.SECONDS ).get();

		} catch ( ExecutionException exception ) {
			Utilities.Error( "Heartbeat acknowledgement timeout: '{}'.", exception.getMessage() );
			webSocket.sendClose( 1002, "Never received heartbeat acknowledgement" );

		} catch ( InterruptedException exception ) {
			Utilities.Error( "Heartbeat acknowledgement interrupted: '{}'.", exception.getMessage() );

		} catch ( CancellationException exception ) {
			Utilities.Error( "Heartbeat acknowledgement cancelled: '{}'.", exception.getMessage() );

		} catch ( Exception exception ) {
			Utilities.Error( exception );
			webSocket.sendClose( 1002, "Internal error" );
		}

	}

	private void StartHeartbeating( WebSocket webSocket, int interval ) {

		Utilities.Log( "Started heartbeating every {} seconds.", interval / 1000.0 );

		boolean sentInitialBeat = false;

		while ( IsConnected( webSocket ) && !heartbeatFuture.isCancelled() ) {

			try {
				if ( !sentInitialBeat ) {
					long initialInterval = Math.round( interval * Math.random() );

					//noinspection BusyWait
					Thread.sleep( initialInterval );
					sentInitialBeat = true;

				} else {
					//noinspection BusyWait
					Thread.sleep( interval );
				}

			} catch ( InterruptedException exception ) {
				Utilities.Error( "Heartbeating interrupted: '%s'", exception.getMessage() );

			} catch ( CancellationException exception ) {
				Utilities.Error( "Heartbeating cancelled: '{}'.", exception.getMessage() );

			} catch ( Exception exception ) {
				Utilities.Error( exception );
				webSocket.sendClose( 1002, "Internal error" );
			}

			if ( !IsConnected( webSocket ) || heartbeatFuture.isCancelled() ) break;

			this.SendHeartbeat( webSocket );

		}

		Utilities.Log( "Finished heartbeating." );

	}

	// Runs when the websocket connection opens
	@Override
	public void onOpen( WebSocket webSocket ) {

		Utilities.Log( "Gateway connection opened." );

		// Run default action
		WebSocket.Listener.super.onOpen( webSocket );

	}

	// Runs when the websocket connection closes
	@Override
	public CompletionStage<?> onClose( WebSocket webSocket, int code, String reason ) {

		Utilities.Log( "Gateway connection closed. ({}, '{}').", code, reason );

		// Stop heartbeating
		try {
			if ( this.heartbeatFuture != null ) this.heartbeatFuture.cancel( true );
			if ( this.heartbeatAcknowledgementFuture != null ) this.heartbeatAcknowledgementFuture.cancel( true );

			if ( this.heartbeatFuture != null ) this.heartbeatFuture.join();
			if ( this.heartbeatAcknowledgementFuture != null ) this.heartbeatAcknowledgementFuture.join();

		} catch ( CancellationException exception ) {
			Utilities.Error( "Cancel heartbeating cancelled: '{}'", exception.getMessage() );

		} catch ( CompletionException exception ) {
			Utilities.Error( "Cancel heartbeating exception: '{}'", exception.getMessage() );

		} catch ( Exception exception ) {
			Utilities.Error( exception );
		}

		// Cleanup for the next run
		this.messageFragments.clear();
		this.sequenceNumber = null;
		this.heartbeatFuture = null;
		this.heartbeatAcknowledgementFuture = null;

		// Complete the future to indicate the connection is now closed
		connectionClosedFuture.complete( code != 1000 );

		// Return default action
		return WebSocket.Listener.super.onClose( webSocket, code, reason );

	}

	// Runs when the a message is received from the websocket server
	@Override
	public CompletionStage<?> onText( WebSocket webSocket, CharSequence messageFragment, boolean isLastFragment ) {

		// Add this fragment to the array
		messageFragments.add( messageFragment );

		// If this is the last message fragment, then we have the entire message!
		if ( isLastFragment ) {

			// Join the message fragments together, and clear for the next run
			String message = String.join( "", messageFragments );
			messageFragments.clear();

			// Parse the message as JSON
			JsonObject payload = JsonParser.parseString( message ).getAsJsonObject();

			// Error if an opcode was not included
			if ( payload.get( "op" ).isJsonNull() ) {
				Utilities.Error( "Gateway payload operation code is invalid." );
				webSocket.sendClose( 1002, "Received invalid operation code" );
				return null;
			}

			// Update the sequence number if one is included
			if ( payload.has( "s" ) && !payload.get( "s" ).isJsonNull() ) this.sequenceNumber = payload.get( "s" ).getAsInt();

			// Get the operation code from the payload
			int operationCode = payload.get( "op" ).getAsInt();

			// If this is the initial welcome message...
			if ( operationCode == OperationCode.Hello && ( payload.has( "d" ) && !payload.get( "d" ).isJsonNull() ) ) {

				// Start heartbeating using the provided interval
				int interval = payload.getAsJsonObject( "d" ).get( "heartbeat_interval" ).getAsInt();
				this.heartbeatFuture = CompletableFuture.runAsync( () -> this.StartHeartbeating( webSocket, interval ) );

				// Create the identify payload
				JsonObject identifyProperties = new JsonObject();
				identifyProperties.addProperty( "os", System.getProperty( "os.name" ) );
				identifyProperties.addProperty( "browser", Config.Get( "discord.library", null ) );
				identifyProperties.addProperty( "device", Config.Get( "discord.library", null ) );

				JsonObject identifyData = new JsonObject();
				identifyData.addProperty( "token", Config.Get( "discord.token", null ) );
				identifyData.addProperty( "intents", 1 << 9 | 1 << 15 ); // GUILD_MESSAGES and MESSAGE_CONTENT
				identifyData.addProperty( "large_threshold", 250 );
				identifyData.addProperty( "compress", USE_COMPRESSION );
				identifyData.add( "properties", identifyProperties );

				JsonObject identifyPayload = new JsonObject();
				identifyPayload.addProperty( "op", OperationCode.Identify );
				identifyPayload.add( "d", identifyData );

				// Send the identify payload
				webSocket.sendText( identifyPayload.toString(), true );

			} else if ( operationCode == OperationCode.HeartbeatAcknowledgement ) {
				if ( this.heartbeatAcknowledgementFuture == null ) {
					Utilities.Error( "Not ready for heartbeat acknowledgement" );
					webSocket.sendClose( 1002, "Received heartbeat acknowledgement too early" );
					return null;
				}

				this.heartbeatAcknowledgementFuture.complete( null );

			// Send a heartbeat if the gateway requests it
			} else if ( operationCode == OperationCode.Heartbeat ) {
				this.SendHeartbeat( webSocket );

			} else if ( operationCode == OperationCode.InvalidSession ) {
				Utilities.Error( "The session is invalid." );
				webSocket.sendClose( 1002, "Session reported as invalid" );

			} else if ( operationCode == OperationCode.Reconnect ) {
				Utilities.Log( "Gateway requested reconnect." );
				webSocket.sendClose( 1000, "Reconnect requested" );

			} else if ( operationCode == OperationCode.Dispatch && ( payload.has( "t" ) && payload.get( "t" ).isJsonPrimitive() ) && ( payload.has( "d" ) && payload.get( "d" ).isJsonObject() ) ) {

				String eventName = payload.get( "t" ).getAsString();
				JsonObject eventData = payload.getAsJsonObject( "d" );

				if ( eventName.equals( "READY" ) ) {
					JsonObject user = eventData.getAsJsonObject( "user" );
					String userName = user.get( "username" ).getAsString();
					Integer userTag = user.get( "discriminator" ).getAsInt();
					String userId = user.get( "id" ).getAsString();

					Utilities.Log( "Ready as '{}#{}' ({})", userName, userTag, userId );

				} else if ( eventName.equals( "MESSAGE_CREATE" ) ) {
					String messageContent = eventData.get( "content" ).getAsString();
					String messageId = eventData.get( "id" ).getAsString();
					boolean isWebhook = eventData.has( "webhook_id" );
					String channelId = eventData.get( "channel_id" ).getAsString();

					if ( !isWebhook ) {

						JsonObject messageAuthor = eventData.getAsJsonObject( "author" );
						String authorName = messageAuthor.get( "username" ).getAsString();
						Integer authorTag = messageAuthor.get( "discriminator" ).getAsInt();
						String authorId = messageAuthor.get( "id" ).getAsString();
						boolean authorIsBot = ( messageAuthor.has( "bot" ) && messageAuthor.get( "bot" ).getAsBoolean() );
						boolean authorIsSystem = ( messageAuthor.has( "system" ) && messageAuthor.get( "system" ).getAsBoolean() );

						if ( channelId.equals( Config.Get( "discord.channel.relay", null ) ) && messageContent.length() > 0 && !authorIsBot && !authorIsSystem ) {

							Utilities.Log( "Relaying Discord message '{}' ({}) from user '{}#{}' ({}).", messageContent, messageId, authorName, authorTag, authorId );

							try {
								Utilities.BroadcastDiscordMessage( authorName, messageContent );

							} catch ( Exception exception ) {
								Utilities.Error( exception );
								webSocket.sendClose( 1002, "Internal error" );
							}

						}

					}

				} else {
					Utilities.Warn( "Unrecognised dispatch event: '{}'.", eventName );
				}

			// Unknown operation code?
			} else {
				Utilities.Warn( "Unrecognised gateway operation code: '{}'.", operationCode );
			}

		}

		// Return default action
		return WebSocket.Listener.super.onText( webSocket, messageFragment, isLastFragment );

	}

	// Runs when an error occurs on the websocket
	@Override
	public void onError( WebSocket webSocket, Throwable exception ) {

		Utilities.Error( "Gateway connection error: '{}'.", exception.getMessage() );

		// Run default action
		WebSocket.Listener.super.onError( webSocket, exception );

	}

}
