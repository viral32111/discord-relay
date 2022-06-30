package com.viral32111.discordrelay;

import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class Utilities {

	public static CompletableFuture<HttpResponse<String>> HttpRequest( String method, String url, Map<String, String> headers, @Nullable String body ) {
		HttpRequest.Builder builder = HttpRequest.newBuilder();

		// Set URL
		builder.uri( URI.create( url ) );

		// Set method and any body
		builder.method( method, ( body != null ? HttpRequest.BodyPublishers.ofString( body ) : HttpRequest.BodyPublishers.noBody() ) );

		// Set additional headers
		headers.forEach( builder::header );

		// Set permanent headers
		builder.header( "User-Agent", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );
		builder.header( "From", Objects.requireNonNull( Config.Get( "http.from" ) ) );

		// Send request and return asynchronous task
		return DiscordRelay.HTTP_CLIENT.sendAsync( builder.build(), HttpResponse.BodyHandlers.ofString() );
	}

	public static CompletableFuture<HttpResponse<String>> sendToWebhook( String destination, JsonObject payload ) {
		return HttpRequest( "POST", destination, Map.of(
			"Content-Type", "application/json"
		), payload.toString() );
	}

	public static String currentDateTime( String format ) {
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
	}
}
