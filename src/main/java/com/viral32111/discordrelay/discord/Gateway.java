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

	// Future that completes when the websocket connection closes, used for reconnecting
	// NOTE: Completion type is any as there is no need to complete this with data
	private static CompletableFuture<?> connectionClosedFuture = null;

	// Compression should not be used, it is not implemented in this class
	private static final boolean USE_COMPRESSION = false;

	// Holds a list of  received message fragments for forming the final message
	private final ArrayList<CharSequence> messageFragments = new ArrayList<>();

	// The current payload sequence number
	private Integer sequenceNumber = null;

	// The futures for heartbeating
	private CompletableFuture<?> heartbeatFuture = null;
	private CompletableFuture<?> heartbeatAcknowledgementFuture = null;

	// Used to gracefully disconnect
	private static WebSocket webSocketConnection = null;
	private static boolean doNotReconnect = false;

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

	public static void Stop() {
		if ( webSocketConnection == null || webSocketConnection.isInputClosed() || webSocketConnection.isOutputClosed() ) throw new RuntimeException( "Cannot disconnect websocket that is not connected" );

		Utilities.Log( "Closing the websocket..." );
		doNotReconnect = true;
		webSocketConnection.sendClose( 1000, "Goodbye" );
	}

	// Connects to a websocket server, and reconnects whenever the close future completes
	private static void Connect( URI url, Gateway listener ) {
		Utilities.Log( "Connect() called" );

		// Create the future for closing
		connectionClosedFuture = new CompletableFuture<>();

		// Asynchronously connect to the gateway, using the provided URL and options from the configuration
		Utilities.HTTP_CLIENT.newWebSocketBuilder()

			// Additional headers in the upgrade request
			.header( "User-Agent", Config.Get( "http.user-agent", null ) )
			.header( "From", Config.Get( "http.from", null ) )

			// Upgrade request timeout to the server
			.connectTimeout( Duration.ofSeconds( Config.Get( "http.timeout" ) )  )

			// URL and listener instance of this class
			.buildAsync( url, listener );

		// Reconnect to the same URL and with the same listener instance whenever the future completes
		connectionClosedFuture.thenAccept( ( $ ) -> {
			if ( !doNotReconnect ) {
				Utilities.Log( "Reconnecting..." );
				Connect( url, listener );
			} else {
				Utilities.Log( "Not reconnecting, this is the end" );
			}
		} );

	}

	// Check if the websocket is currently open
	private boolean IsConnected( WebSocket webSocket ) {
		return !( webSocket.isInputClosed() || webSocket.isOutputClosed() );
	}

	/*private void Send( WebSocket webSocket, int operationCode, @Nullable JsonElement data ) {

		try {
			JsonObject payload = new JsonObject();
			payload.addProperty( "op", operationCode );
			payload.add( "d", data );

			Utilities.Log( "Sending: {}", payload.toString() );

			//webSocket.sendText( payload.toString(), true );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}

	}*/

	private void SendHeartbeat( WebSocket webSocket ) {

		heartbeatAcknowledgementFuture = new CompletableFuture<>();

		try {
			Utilities.Log( "Sending heartbeat with sequence number: {}...", sequenceNumber );
			//this.Send( webSocket, OperationCode.Heartbeat, new JsonPrimitive( this.sequenceNumber ) );

			JsonObject heartbeatPayload = new JsonObject();
			heartbeatPayload.addProperty( "op", OperationCode.Heartbeat );
			heartbeatPayload.addProperty( "d", this.sequenceNumber );

			Utilities.Log( "Sending: {}", heartbeatPayload.toString() );
			webSocket.sendText( heartbeatPayload.toString(), true );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}

		Utilities.Log( "Registered heartbeat acknowledgement callback" );
		heartbeatAcknowledgementFuture.thenAccept( ( $ ) -> {
			Utilities.Log( "Received heartbeat acknowledgement" );
		} );

		try {
			Utilities.Log( "Waiting for heartbeat acknowledgement, or timing out..." );
			heartbeatAcknowledgementFuture.orTimeout( 5, TimeUnit.SECONDS ).get();
		} catch ( InterruptedException exception ) {
			Utilities.Log( "Heartbeat acknowledgement future interrupted! ({})", exception.getMessage() );
		} catch ( ExecutionException exception ) {
			Utilities.Log( "Heartbeat acknowledgement future completed exceptionally! ({})", exception.getMessage() );
			webSocket.sendClose( 1000, "Never received heartbeat acknowledgement" );
		} catch ( CancellationException exception ) {
			Utilities.Log( "Heartbeat acknowledgement future cancelled! ({})", exception.getMessage() );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}

		/*Utilities.Log( "Waiting to receive heartbeat acknowledgement, or dying after 5 seconds..." );
		heartbeatAcknowledgementFuture.orTimeout( 5, TimeUnit.SECONDS ).thenAccept( ( $ ) -> {
			Utilities.Log( "Received heartbeat acknowledgement within 5 seconds" );
		} ).exceptionally( ( exception ) -> {
			Utilities.LOGGER.warn( "Never received heartbeat acknowledgement" );
			webSocket.sendClose( 1000, "Never received heartbeat acknowledgement" );
			return null;
		} );*/

	}

	private void StartHeartbeating( WebSocket webSocket, int interval ) {

		Utilities.Log( "Started heartbeating every {} ms...", interval );

		boolean sentInitialBeat = false;

		while ( IsConnected( webSocket ) && !heartbeatFuture.isCancelled() ) {

			try {
				Utilities.Log( "Waiting interval {} ms...", interval );

				if ( !sentInitialBeat ) {
					long initialInterval = Math.round( interval * Math.random() );

					Utilities.Log( "Initial heartbeat of {} ms", initialInterval );

					//noinspection BusyWait
					Thread.sleep( initialInterval );
					sentInitialBeat = true;

				} else {
					Utilities.Log( "Regular heartbeat of {} ms", interval );

					//noinspection BusyWait
					Thread.sleep( interval );
				}

			} catch ( InterruptedException exception ) {
				Utilities.Log( "Wait heartbeat interval interrupted! ({})", exception.getMessage() );
			} catch ( Exception exception ) {
				exception.printStackTrace();
			}

			Utilities.Log( "Interval waited, checking if should still run..." );
			if ( !IsConnected( webSocket ) || heartbeatFuture.isCancelled() ) break;

			Utilities.Log( "Sending periodic heartbeat..." );
			this.SendHeartbeat( webSocket );

			Utilities.Log( "Should loop still run: {}, {}", IsConnected( webSocket ), !heartbeatFuture.isCancelled() );

		}

		Utilities.Log( "Finished heartbeating." );

	}

	// Runs when the websocket connection opens
	@Override
	public void onOpen( WebSocket webSocket ) {

		Utilities.Log( "Gateway connection opened." );

		webSocketConnection = webSocket;

		// Run default action
		WebSocket.Listener.super.onOpen( webSocket );

	}

	// Runs when the websocket connection closes
	@Override
	public CompletionStage<?> onClose( WebSocket webSocket, int code, String reason ) {

		Utilities.Log( "Gateway connection closed: {} ({}).", code, reason );

		// Stop heartbeating
		try {
			if ( this.heartbeatFuture != null ) {
				Utilities.Log( "Cancelling heartbeat future..." );
				this.heartbeatFuture.cancel( true );
			}

			if ( this.heartbeatAcknowledgementFuture != null ) {
				Utilities.Log( "Cancelling heartbeat acknowledgement future..." );
				this.heartbeatAcknowledgementFuture.cancel( true );
			}

			if ( this.heartbeatFuture != null ) {
				Utilities.Log( "Waiting for heartbeat future..." );
				this.heartbeatFuture.join();
			}

			if ( this.heartbeatAcknowledgementFuture != null ) {
				Utilities.Log( "Waiting for heartbeat acknowledgement future..." );
				this.heartbeatAcknowledgementFuture.join();
			}
		} catch ( CancellationException exception ) {
			Utilities.Log( "Cancellation exception while cancelling/waiting heartbeat futures! ({})", exception.getMessage() );
		} catch ( CompletionException exception ) {
			Utilities.Log( "Completion exception while cancelling/waiting heartbeat futures! ({})", exception.getMessage() );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}


		// Cleanup for the next run
		this.messageFragments.clear();
		this.sequenceNumber = null;
		this.heartbeatFuture = null;
		this.heartbeatAcknowledgementFuture = null;

		// Complete the future to indicate the connection is now closed
		Utilities.Log( "Completing connection close future..." );
		connectionClosedFuture.complete( null );

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
			Utilities.Log( "Gateway said: '{}'", payload.toString() );

			// Error if an opcode was not included
			if ( payload.get( "op" ).isJsonNull() ) throw new RuntimeException( "Gateway payload operation code is invalid" );

			// Update the sequence number if one is included
			if ( payload.has( "s" ) && !payload.get( "s" ).isJsonNull() ) this.sequenceNumber = payload.get( "s" ).getAsInt();

			// Store the opcode in the payload for easy access
			int operationCode = payload.get( "op" ).getAsInt();

			// If this is the initial welcome message...
			if ( operationCode == OperationCode.Hello && ( payload.has( "d" ) && !payload.get( "d" ).isJsonNull() ) ) {

				// Start heartbeating using the provided interval
				int interval = payload.getAsJsonObject( "d" ).get( "heartbeat_interval" ).getAsInt();
				this.heartbeatFuture = CompletableFuture.runAsync( () -> this.StartHeartbeating( webSocket, interval ) );

				// Create an identify payload
				JsonObject identifyProperties = new JsonObject();
				identifyProperties.addProperty( "os", System.getProperty( "os.name" ) );
				identifyProperties.addProperty( "browser", Config.Get( "discord.library", null ) );
				identifyProperties.addProperty( "device", Config.Get( "discord.library", null ) );

				JsonObject identifyPayload = new JsonObject();
				identifyPayload.addProperty( "token", Config.Get( "discord.token", null ) );
				identifyPayload.addProperty( "intents", 1 << 9 ); // GUILD_MESSAGES
				identifyPayload.addProperty( "large_threshold", 250 );
				identifyPayload.addProperty( "compress", USE_COMPRESSION );
				identifyPayload.add( "properties", identifyProperties );

				// Send the identify payload
				//this.Send( webSocket, OperationCode.Identify, identifyPayload );

			} else if ( operationCode == OperationCode.HeartbeatAcknowledgement ) {
				Utilities.Log( "Got heartbeat ack opcode" );
				if ( this.heartbeatAcknowledgementFuture == null ) throw new RuntimeException( "Not ready for heartbeat acknowledgement" );
				this.heartbeatAcknowledgementFuture.complete( null );

			// Send a heartbeat if the gateway requests it
			} else if ( operationCode == OperationCode.Heartbeat ) {
				Utilities.Log( "Heartbeat requested" );
				this.SendHeartbeat( webSocket );

			} else if ( operationCode == OperationCode.Dispatch && ( payload.has( "t" ) && !payload.get( "t" ).isJsonNull() ) && ( payload.has( "d" ) && !payload.get( "d" ).isJsonNull() ) ) {
				Utilities.Log( "Event dispatch..." );

				if ( payload.get( "t" ).getAsString().equals( "READY" ) ) {
					//Utilities.Log( "We are ready! {}", payload.getAsJsonObject( "d ").getAsJsonObject( "user" ).get( "username" ).getAsString() );

				} else {
					Utilities.LOGGER.warn( "Unknown event dispatch: '{}'", payload.get( "t" ).getAsString() );
				}


			// TODO: Invalid Session
			// TODO: Reconnect

			// Unknown operation code?
			} else {
				Utilities.LOGGER.warn( "Unknown gateway operation code: '{}'", operationCode );
			}

		}

		// Return default action
		return WebSocket.Listener.super.onText( webSocket, messageFragment, isLastFragment );

	}

	// Runs when an error occurs on the websocket
	@Override
	public void onError( WebSocket webSocket, Throwable error ) {

		Utilities.LOGGER.error( error.getMessage() );

		// Run default action
		WebSocket.Listener.super.onError( webSocket, error );

	}

	/*
	public CompletionStage<?> onText( WebSocket webSocket, CharSequence messageFragment, boolean isLastMessage ) {
		//DiscordRelay.logger.info( "onText() -> '{}' | {}", messageFragment, isLastMessage );

		receivedTextSequences.add( messageFragment );

		if ( isLastMessage ) {
			String message = String.join( "", receivedTextSequences );
			receivedTextSequences.clear();

			JsonObject payload = JsonParser.parseString( message ).getAsJsonObject();
			//DiscordRelay.logger.info( payload.toString() );

			if ( !payload.get( "op" ).isJsonNull() ) {
				int operationCode = payload.get( "op" ).getAsInt();
				//var sequenceNumber = payload.get( "s" ); // Should be an integer, but can be null
				//String eventType = payload.get( "t" ).getAsString(); // Could be null
				//var eventData = payload.get( "d" ); // Possibly don't know the type, could even be null

				if ( !payload.get( "s" ).isJsonNull() ) {
					latestSequenceNumber =  payload.get( "s" ).getAsInt();
					//DiscordRelay.logger.info( "Sequence number updated to {}", latestSequenceNumber );
				} /*else {
					DiscordRelay.logger.warn( "sequence number is null?" );
				}*/

				/*if ( operationCode == 10 ) {
					DiscordRelay.logger.info( "Received hello!" );

					if ( !payload.get( "d" ).isJsonNull() ) {
						heartbeatInterval = payload.get( "d" ).getAsJsonObject().get( "heartbeat_interval" ).getAsInt();
						DiscordRelay.logger.info( "Heartbeat interval is {}", heartbeatInterval );

						heartbeater = CompletableFuture.runAsync( this::startHeartbeating );

						DiscordRelay.logger.info( "We should identify now..." );

						JsonObject identifyDataProperties = new JsonObject();
						identifyDataProperties.addProperty( "$os", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );
						identifyDataProperties.addProperty( "$browser", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );
						identifyDataProperties.addProperty( "$device", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );

						JsonObject identifyDataPresence = new JsonObject();
						identifyDataPresence.addProperty( "status", "offline" );

						JsonObject identifyData = new JsonObject();
						identifyData.addProperty( "token", Objects.requireNonNull( Config.Get( "discord.token" ) ) );
						identifyData.addProperty( "intents", ( 1 << 9 ) ); // Server Messages
						identifyData.add( "properties", identifyDataProperties );

						JsonObject identifyPayload = new JsonObject();
						identifyPayload.addProperty( "op", 2 );
						identifyPayload.add( "d", identifyData );

						myself.sendText( identifyPayload.toString(), true );
					} else {
						DiscordRelay.logger.warn( "The event data is null?" );
					}
				} else if ( operationCode == 11 ) {
					DiscordRelay.logger.info( "Received heartbeat acknowledgement!" );
				} else if ( operationCode == 1 ) {
					DiscordRelay.logger.info( "Gateway wants to know if we're still here :o" );

					JsonObject heartbeatPayload = new JsonObject();
					heartbeatPayload.addProperty( "op", 1 );
					heartbeatPayload.addProperty( "d", latestSequenceNumber );
					myself.sendText(heartbeatPayload.toString(), true);
				} else if ( operationCode == 0 ) {
					if ( !payload.get( "t" ).isJsonNull() || !payload.get( "d" ).isJsonNull() ) {
						String type = payload.get( "t" ).getAsString();
						JsonObject data = payload.get( "d" ).getAsJsonObject();

						//DiscordRelay.logger.info( "Received event dispatch for: {}", type );

						if ( type.equals( "READY" ) ) {
							DiscordRelay.logger.info( "Connected as '{}#{}' ({})", data.get( "user" ).getAsJsonObject().get( "username" ).getAsString(), data.get( "user" ).getAsJsonObject().get( "discriminator" ).getAsInt(), data.get( "user" ).getAsJsonObject().get( "id" ).getAsString() );

						} else if ( type.equals( "MESSAGE_CREATE" ) ) {
							String channelID = data.get( "channel_id" ).getAsString();

							if ( channelID.equals( Objects.requireNonNull( Config.Get( "discord.channel.relay" ) ) ) && data.get( "webhook_id" ) == null ) {
								JsonObject author = data.get( "author" ).getAsJsonObject();
								String authorName = author.get( "username" ).getAsString();

								JsonObject member = data.get( "member" ).getAsJsonObject();

								if ( member.has( "nick" ) && !member.get( "nick" ).isJsonNull() ) authorName = member.get( "nick" ).getAsString();

								String content = data.get( "content" ).getAsString();
								if ( !content.equals( "" ) ) {
									//DiscordRelay.logger.info( "Received relayed Discord message '{}' from '{}'!", content, authorName );

									DiscordRelay.broadcastDiscordMessage( authorName, content );
								}

								//DiscordRelay.executeServerCommand( String.format( "tellraw @a [{\"text\":\"(Discord) \",\"color\":\"blue\"},{\"text\":\"%s\",\"color\":\"green\"},{\"text\":\": %s\",\"color\":\"white\"}]", username, content ) );
							} else {
								DiscordRelay.logger.warn( "Ignoring message for channel {}.", channelID );
							}
						} else {
							DiscordRelay.logger.warn( "Ignoring event dispatch for {}.", type );
						}
					} else {
						DiscordRelay.logger.warn( "The event type and/or data is null?" );
					}
				} else {
					DiscordRelay.logger.warn( "Ignoring operation code {}.", operationCode );
				}
			} else {
				DiscordRelay.logger.warn( "The operation code is null?" );
			}
		}

		return WebSocket.Listener.super.onText( webSocket, messageFragment, isLastMessage );
	}*/

}
