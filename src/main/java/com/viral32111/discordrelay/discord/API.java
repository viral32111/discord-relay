package com.viral32111.discordrelay.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;

import javax.annotation.Nullable;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Encapsulates everything for making requests to the Discord API
public class API {

	// Makes an asynchronous request to the API
	public static CompletableFuture<JsonObject> Request( String method, String endpoint, @Nullable JsonObject data ) {

		// Create a future to return then complete later on
		CompletableFuture<JsonObject> future = new CompletableFuture<>();

		// The HTTP headers for this request
		// NOTE: Does not include User-Agent or From as the Utilities.HttpRequest() method adds those
		Map<String, String> requestHeaders = Map.of(
			"Accept", "application/json, */*",
			"Content-Type", "application/json",
			"Authorization", String.format( "Bot %s", Config.Get( "discord.token", null ) )
		);

		// Send a HTTP request to the provided API endpoint, with an optional JSON payload
		Utilities.HttpRequest( method, String.format( "https://%s/%s", Config.Get( "discord.url", null ), endpoint ), requestHeaders, ( data != null ? data.toString() : null ) ).thenAccept( ( HttpResponse<String> response ) -> {

			// Error if the request was unsuccessful
			if ( response.statusCode() < 200 || response.statusCode() > 299 ) {
				future.completeExceptionally( new Exception( String.format( "API request unsuccessful with code: %d", response.statusCode() ) ) );
				return;
			}

			// Store the content of the response, and error if it is empty
			String responseBody = response.body();
			if ( responseBody.length() <= 0 ) {
				future.completeExceptionally( new Exception( "API response has no body." ) );
				return;
			}

			// Parse the response content as JSON, and complete the future
			JsonObject responseJson = JsonParser.parseString( responseBody ).getAsJsonObject();
			future.complete( responseJson );

		} );

		// Return the future created at the start
		return future;

	}

	// Helper to send a message to a webhook
	public static CompletableFuture<JsonObject> ExecuteWebhook( String identifierAndToken, JsonObject payload ) {
		return Request( "POST", String.format( "webhook/%s", identifierAndToken ), payload );
	}

}
