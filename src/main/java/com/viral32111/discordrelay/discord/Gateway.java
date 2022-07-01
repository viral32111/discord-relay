package com.viral32111.discordrelay.discord;

import com.google.gson.JsonObject;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.DiscordRelay;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// Client for the Discord Gateway
public class Gateway implements WebSocket.Listener {

	// Future that completes when the websocket connection closes, used for reconnecting
	// NOTE: Completion type is any as there is no need to complete this with data
	private static CompletableFuture<?> connectionClosedFuture = null;

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
			.header( "User-Agent", Config.Get( "http.user-agent", null ) )
			.header( "From", Config.Get( "http.from", null ) )
			.connectTimeout( Duration.ofSeconds( Config.Get( "http.timeout" ) )  )
			.buildAsync( url, listener );

		// Reconnect to the same URL and with the same listener instance whenever the future completes
		connectionClosedFuture.thenAccept( ( $ ) -> Connect( url, listener ) );

	}

	// Runs when the websocket connection opens
	@Override
	public void onOpen( WebSocket webSocket ) {
		DiscordRelay.LOGGER.info( "Gateway connection opened." );

		WebSocket.Listener.super.onOpen( webSocket );
	}

	// Runs when the websocket connection closes
	@Override
	public CompletionStage<?> onClose( WebSocket webSocket, int code, String reason ) {
		DiscordRelay.LOGGER.info( "Gateway connection closed: {} ({}).", code, reason );

		connectionClosedFuture.complete( null );

		return WebSocket.Listener.super.onClose( webSocket, code, reason );
	}

	// Runs when the a message is received from the websocket server
	@Override
	public CompletionStage<?> onText( WebSocket webSocket, CharSequence message, boolean isLastMessage ) {
		DiscordRelay.LOGGER.info( "Received message: '{}' (Final? {}).", message, isLastMessage );

		return WebSocket.Listener.super.onText( webSocket, message, isLastMessage );
	}

	// Runs when an error occurs on the websocket
	@Override
	public void onError( WebSocket webSocket, Throwable error ) {
		DiscordRelay.LOGGER.error( error.getMessage() );

		WebSocket.Listener.super.onError( webSocket, error );
	}

}
