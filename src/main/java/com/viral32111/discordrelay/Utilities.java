package com.viral32111.discordrelay;

import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class Utilities {
	public static CompletableFuture<HttpResponse<String>> sendHttpRequest( String method, URI destination, String body, HashMap<String, String> headers ) {
		HttpRequest.Builder builder = HttpRequest.newBuilder();

		// Set URL
		builder.uri( destination );

		// Set method and any body
		builder.method( method, ( body.equals( "" ) ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString( body ) ) );

		// Set additional headers
		headers.forEach( builder::header );

		// Set permanent headers
		builder.header( "User-Agent", Config.httpUserAgent );
		builder.header( "From", Config.httpFrom );

		// Send request and return asynchronous task
		return DiscordRelay.httpClient.sendAsync( builder.build(), HttpResponse.BodyHandlers.ofString() );
	}

	public static CompletableFuture<HttpResponse<String>> sendToWebhook( URI destination, JsonObject payload ) {
		return sendHttpRequest( "POST", destination, payload.toString(), new HashMap<>() {{
			put( "Content-Type", "application/json" );
		}} );
	}

	public static String currentDateTime( String format ) {
		return DateTimeFormatter.ofPattern( format ).format( ZonedDateTime.now( ZoneId.of( "UTC" ) ) );
	}

	public static String getPlayerName( ServerPlayerEntity player ) {
		if ( player.getName().asString().equals( player.getDisplayName().asString() ) ) {
			return player.getName().asString();
		} else {
			return String.format( "%s (%s)", player.getDisplayName().asString(), player.getName().asString() );
		}
	}

	public static String capitalise( String original ) {
		return original.substring( 0, 1 ).toUpperCase().concat( original.substring( 1 ) );
	}

	public static String escapeDiscordMarkdown( String original ) {
		original = original.replace( "~~", "\\~\\~" ); // Strikethrough
		original = original.replace( "_", "\\_\\_" ); // Underline & Italics
		original = original.replace( "*", "\\*" ); // Italics & Bold
		original = original.replace( "`", "\\`" ); // Code-blocks
		original = original.replace( "> ", "\\> " ); // Quotes

		// Mentions too!
		original = original.replace( "@everyone", "@-everyone" );
		original = original.replace( "@here", "@-here" );

		// New lines
		original = original.replace( "\n", " " );

		return original;
	}
}
