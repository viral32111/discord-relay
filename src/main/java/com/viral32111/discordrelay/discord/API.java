package com.viral32111.discordrelay.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;

import javax.annotation.Nullable;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Encapsulates everything for making requests to the Discord API
public class API {

	// Holds what the types of mentions are allowed
	// NOTE: Setup in server initialise method of main class
	public static final JsonObject AllowedMentions = new JsonObject();

	// Makes an asynchronous request to the API
	// TODO: Implement rate-limiting
	public static CompletableFuture<JsonObject> Request( String method, String endpoint, @Nullable JsonObject data, @Nullable String auditLogReason ) {

		// Create a future to return then complete later on
		CompletableFuture<JsonObject> future = new CompletableFuture<>();

		// The HTTP headers for this request
		// NOTE: Does not include User-Agent or From as the Utilities.HttpRequest() method adds those
		HashMap<String, String> requestHeaders = new HashMap<>();
		requestHeaders.put( "Accept", "application/json" );
		requestHeaders.put( "Content-Type", "application/json" );
		requestHeaders.put( "Authorization", String.format( "Bot %s", Config.Get( "discord.token", null ) ) );
		if ( auditLogReason != null ) requestHeaders.put( "X-Audit-Log-Reason", auditLogReason );

		// Send a HTTP request to the provided API endpoint, with an optional JSON payload
		Utilities.HttpRequest( method, String.format( "https://%s/v%s/%s", Config.Get( "discord.api.url", null ), Config.Get( "discord.api.version", null ), endpoint ), requestHeaders, ( data != null ? data.toString() : null ) ).thenAccept( ( HttpResponse<String> response ) -> {

			// Dump rate-limit headers
			Map<String, List<String>> responseHeaders = response.headers().map();
			String limit = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Limit", List.of( "Unknown" ) ) );
			String remaining = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Remaining", List.of( "Unknown" ) ) );
			String reset = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Reset", List.of( "Unknown" ) ) );
			String resetAfter = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Reset-After", List.of( "Unknown" ) ) );
			String bucket = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Bucket", List.of( "Unknown" ) ) );
			Utilities.Debug( "Rate-limit for '{}' '{}': [ Limit: {}, Remaining: {}, Reset: {}, Reset After: {}, Bucket: {} ]", method, endpoint, limit, remaining, reset, resetAfter, bucket );

			// Error if the request was unsuccessful
			if ( response.statusCode() < 200 || response.statusCode() > 299 ) {

				// Was it because of hitting a rate limit?
				if ( response.statusCode() == 429 ) {
					String global = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Global", List.of( "Unknown" ) ) );
					String scope = String.join( ", ", responseHeaders.getOrDefault( "X-RateLimit-Scope", List.of( "Unknown" ) ) );
					Utilities.Debug( "Hit rate-limit for '{}' '{}'! [ Global: {}, Scope: {} ]", method, endpoint, global, scope );

				} else {
					future.completeExceptionally( new Exception( String.format( "Discord API request unsuccessful with code: %d", response.statusCode() ) ) );
				}

				return;
			}

			// Store the content of the response, and error if it is empty
			String responseBody = response.body();
			if ( responseBody.length() <= 0 ) {
				future.completeExceptionally( new Exception( "Discord API response has no body." ) );
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
	public static CompletableFuture<String> ExecuteWebhook( String identifierAndToken, JsonObject payload, boolean isEmbed ) {

		// Future to complete with the identifier of the newly created message
		CompletableFuture<String> messageFuture = new CompletableFuture<>();

		// Is the provided JSON object just an embed?
		if ( isEmbed ) {

			// Array of the provided embed
			JsonArray embeds = new JsonArray();
			embeds.add( payload );

			// Payload to send with the embeds arrays and mentions object
			JsonObject embedsPayload = new JsonObject();
			embedsPayload.add( "embeds", embeds );
			embedsPayload.add( "allowed_mentions", AllowedMentions );

			// Update the parameter
			payload = embedsPayload;

		}

		// Execute the webhook with the (possibly embed wrapped) payload
		// NOTE: The wait parameter makes the server not respond until the message is created
		Request( "POST", String.format( "webhooks/%s?wait=true", identifierAndToken ), payload, null ).thenAccept( ( JsonObject response ) -> {

			// Complete with the identifier of the newly created message
			messageFuture.complete( response.get( "id" ).getAsString() );

		} );

		// Return the future
		return messageFuture;

	}

}
