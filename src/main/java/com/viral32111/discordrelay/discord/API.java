package com.viral32111.discordrelay.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;

import javax.annotation.Nullable;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

// Encapsulates everything for making requests to the Discord API
public class API {

	// Holds what the types of mentions are allowed
	// NOTE: Setup in server initialise method of main class
	public static final JsonObject AllowedMentions = new JsonObject();

	// Makes an asynchronous request to the API
	public static CompletableFuture<JsonObject> Request( String method, String endpoint, @Nullable JsonObject data, @Nullable String auditLogReason ) {

		// Create a future to return then complete later on
		CompletableFuture<JsonObject> future = new CompletableFuture<>();

		// The HTTP headers for this request
		// NOTE: Does not include User-Agent or From as the Utilities.HttpRequest() method adds those
		HashMap<String, String> requestHeaders = new HashMap<>();
		requestHeaders.put( "Accept", "application/json, */*" );
		requestHeaders.put( "Content-Type", "application/json" );
		requestHeaders.put( "Authorization", String.format( "Bot %s", Config.Get( "discord.token", null ) ) );
		if ( auditLogReason != null ) requestHeaders.put( "X-Audit-Log-Reason", auditLogReason );

		// Send a HTTP request to the provided API endpoint, with an optional JSON payload
		Utilities.HttpRequest( method, String.format( "https://%s/v%s/%s", Config.Get( "discord.api.url", null ), Config.Get( "discord.api.version", null ), endpoint ), requestHeaders, ( data != null ? data.toString() : null ) ).thenAccept( ( HttpResponse<String> response ) -> {

			// Error if the request was unsuccessful
			if ( response.statusCode() < 200 || response.statusCode() > 299 ) throw new RuntimeException( String.format( "API request unsuccessful with code: %d", response.statusCode() ) );

			// Store the content of the response, and error if it is empty
			String responseBody = response.body();
			if ( responseBody.length() <= 0 ) throw new RuntimeException( "API response has no body." );

			// Parse the response content as JSON, and complete the future
			JsonObject responseJson = JsonParser.parseString( responseBody ).getAsJsonObject();
			future.complete( responseJson );

		} );

		// Return the future created at the start
		return future;

	}

	// Helper to send a message to a webhook
	public static void ExecuteWebhook( String identifierAndToken, JsonObject payload, boolean isEmbed ) {

		// Is the provided JSON object just an embed?
		if ( isEmbed ) {

			// Array of the provided embed
			JsonArray embeds = new JsonArray();
			embeds.add( payload );

			// Payload to send with the embeds arrays and mentions object
			JsonObject embedsPayload = new JsonObject();
			embedsPayload.add( "embeds", embeds );
			embedsPayload.add( "allowed_mentions", AllowedMentions );

			// Execute the webhook with the wrapped embed payload
			Request( "POST", String.format( "webhooks/%s", identifierAndToken ), embedsPayload, null );

		// Otherwise it must be an entire payload, so just send it as is
		} else {
			Request( "POST", String.format( "webhooks/%s", identifierAndToken ), payload, null );
		}

	}

}
