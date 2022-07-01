package com.viral32111.discordrelay;

import net.minecraft.server.network.ServerPlayerEntity;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Methods & helpers used across the entire mod
public class Utilities {

	// Perform a HTTP request to a specified URL with a custom method, headers, and an optional body
	public static CompletableFuture<HttpResponse<String>> HttpRequest( String method, String url, Map<String, String> headers, @Nullable String body ) {

		DiscordRelay.LOGGER.debug( "'{}' to '{}' with {} bytes", method, url, ( body != null ? body.length() : 0 ) );

		// Create a new request builder
		HttpRequest.Builder builder = HttpRequest.newBuilder();

		// Set connection timeout from the configuration
		builder.timeout( Duration.ofSeconds( Config.Get( "http.timeout" ) ) );

		// Set the target URL
		builder.uri( URI.create( url ) );

		// Set the method, and body if provided
		builder.method( method, ( body != null ? HttpRequest.BodyPublishers.ofString( body ) : HttpRequest.BodyPublishers.noBody() ) );

		// Add any headers provided
		headers.forEach( builder::header );

		// Add the permanent headers
		builder.header( "User-Agent", Config.Get( "http.user-agent", null ) );
		builder.header( "From", Config.Get( "http.from", null ) );

		// Send the request and return the asynchronous task
		return DiscordRelay.HTTP_CLIENT.sendAsync( builder.build(), HttpResponse.BodyHandlers.ofString() );

	}

	/*public static String currentDateTime( String format ) {
		return DateTimeFormatter.ofPattern( format ).format( ZonedDateTime.now( ZoneId.of( "UTC" ) ) );
	}

	public static String getPlayerName( ServerPlayerEntity player ) {
		if ( player.getName().getString().equals( player.getDisplayName().getString() ) ) {
			return player.getName().getString();
		} else {
			return String.format( "%s (%s)", player.getDisplayName().getString(), player.getName().getString() );
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
	}*/
}
