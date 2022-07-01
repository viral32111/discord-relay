package com.viral32111.discordrelay.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.DiscordRelay;
import com.viral32111.discordrelay.discord.types.OperationCode;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// Client for the Discord Gateway
public class Gateway implements WebSocket.Listener {

	// Future that completes when the websocket connection closes, used for reconnecting
	// NOTE: Completion type is any as there is no need to complete this with data
	private static CompletableFuture<?> connectionClosedFuture = null;

	// Holds a list of  received message fragments for forming the final message
	private final ArrayList<CharSequence> messageFragments = new ArrayList<>();

	// Holds the current payload sequence number
	private Integer sequenceNumber = null;

	// Starts the initial connection to the gateway
	public static void Start() {

		// Fetch the gateway URL
		API.Request( "GET", "gateway/bot", null ).thenAccept( ( JsonObject response ) -> {

			// Add connection properties to the returned URL
			URI url = URI.create( String.format( "%s/?v=%s&encoding=json&compress=false", response.get( "url" ).getAsString(), Config.Get( "discord.api.version", null ) ) );

			// Begin the first connection to the above URL
			Connect( url, new Gateway() );

		} );

	}

	// Connects to a websocket server, and reconnects whenever the close future completes
	private static void Connect( URI url, Gateway listener ) {

		// Create the future for closing
		connectionClosedFuture = new CompletableFuture<>();

		// Asynchronously connect to the gateway, using the provided URL and options from the configuration
		DiscordRelay.HTTP_CLIENT.newWebSocketBuilder()

			// Additional headers in the upgrade request
			.header( "User-Agent", Config.Get( "http.user-agent", null ) )
			.header( "From", Config.Get( "http.from", null ) )

			// Upgrade request timeout to the server
			.connectTimeout( Duration.ofSeconds( Config.Get( "http.timeout" ) )  )

			// URL and listener instance of this class
			.buildAsync( url, listener );

		// Reconnect to the same URL and with the same listener instance whenever the future completes
		connectionClosedFuture.thenAccept( ( $ ) -> Connect( url, listener ) );

	}

	// Runs when the websocket connection opens
	@Override
	public void onOpen( WebSocket webSocket ) {
		DiscordRelay.LOGGER.info( "Gateway connection opened." );

		// Run default action
		WebSocket.Listener.super.onOpen( webSocket );

	}

	// Runs when the websocket connection closes
	@Override
	public CompletionStage<?> onClose( WebSocket webSocket, int code, String reason ) {
		DiscordRelay.LOGGER.info( "Gateway connection closed: {} ({}).", code, reason );

		// Cleanup for the next run
		messageFragments.clear();
		sequenceNumber = null;

		// Complete the future to indicate the connection is now closed
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
			DiscordRelay.LOGGER.info( "Gateway said: '{}'", payload.toString() );

			// Error if an opcode was not included
			if ( payload.get( "op" ).isJsonNull() ) throw new RuntimeException( "Gateway payload operation code is invalid" );

			// Update the sequence number if one is included
			if ( payload.has( "s" ) && !payload.get( "s" ).isJsonNull() ) this.sequenceNumber = payload.get( "s" ).getAsInt();

			// Store the opcode in the payload for easy access
			int operationCode = payload.get( "op" ).getAsInt();

			if ( operationCode == OperationCode.Hello && ( payload.has( "d" ) && !payload.get( "d" ).isJsonNull() ) ) {
				int interval = payload.getAsJsonObject( "d" ).get( "heartbeat_interval" ).getAsInt();

				// TODO: Start heartbeating
			}

		}

		// Return default action
		return WebSocket.Listener.super.onText( webSocket, messageFragment, isLastFragment );

	}

	// Runs when an error occurs on the websocket
	@Override
	public void onError( WebSocket webSocket, Throwable error ) {
		DiscordRelay.LOGGER.error( error.getMessage() );

		// Run default action
		WebSocket.Listener.super.onError( webSocket, error );

	}

}
