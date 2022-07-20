package com.viral32111.discordrelay;

import com.google.gson.JsonObject;
import com.viral32111.discordrelay.discord.API;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Methods & helpers used across the entire mod
public class Utilities {

	// Create a HTTP client to use across the entire mod
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	// Get an instance of the logger to use across the entire mod
	// NOTE: The name used here is the same as the Mod Identifier in the Fabric configuration file
	public static final Logger LOGGER = LogManager.getLogger( "discordrelay" );

	// Perform a HTTP request to a specified URL with a custom method, headers, and an optional body
	public static CompletableFuture<HttpResponse<String>> HttpRequest( String method, String url, Map<String, String> headers, @Nullable String body ) {

		LOGGER.debug( "'{}' to '{}' with {} bytes", method, url, ( body != null ? body.length() : 0 ) );

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
		return Utilities.HTTP_CLIENT.sendAsync( builder.build(), HttpResponse.BodyHandlers.ofString() );

	}

	// Checks if a player has a nickname by checking their display name is different to their account anme
	public static boolean PlayerHasNickname( ServerPlayerEntity player ) {
		return !player.getDisplayName().getString().equals( player.getName().getString() );
	}

	// Returns a player's name & nickname if they have a nickname, otherwise just the name
	public static String GetPlayerName( ServerPlayerEntity player ) {

		// If the player has a nickname, return both names
		if ( PlayerHasNickname( player ) ) {
			return String.format( "%s (%s)", player.getDisplayName().getString(), player.getName().getString() );

		// Otherwise, return just the name
		} else {
			return player.getName().getString();
		}

	}

	public static String CurrentDateTime() {
		return DateTimeFormatter.ofPattern( Config.Get( "log-date-format", null ) ).format( ZonedDateTime.now( ZoneId.of( "UTC" ) ) );
	}

	// Helper to update the server status in the category name
	// TODO: Implement rate-limiting & queue
	public static void UpdateCategoryStatus( String status, @Nullable String auditLogReason ) {

		// Create the payload to send
		JsonObject payload = new JsonObject();
		payload.addProperty( "name", Config.Get( "discord.category.name", Map.of( "status", status ) ) );

		// Send the request
		API.Request( "PATCH", String.format( "channels/%s", Config.Get( "discord.category.id", null ) ), payload, auditLogReason );

	}

	// Helper to display a message in the console with a prefix to identify the message is from this mod
	// TODO: This but for warnings, errors & debug
	public static void Log( String message, Object... params ) {
		LOGGER.info( String.format( "[Discord Relay] %s", message ), params );
	}

	// Helper to convert a unit of time (e.g. seconds, milliseconds) to a pretty duration string (x hours, x minutes, x seconds)
	public static String ToPrettyDuration( long totalDuration, TimeUnit timeUnit ) {

		// Will hold the pretty duration strings if they are not zero
		List<String> prettyDurations = new ArrayList<>();

		// Get the duration as each unit
		long hours = timeUnit.toHours( totalDuration );
		long minutes = timeUnit.toMinutes( totalDuration );
		long seconds = timeUnit.toSeconds( totalDuration );

		// Add it to the array if it is not zero
		if ( hours != 0 ) prettyDurations.add( String.format( "%d hours", hours ) );
		if ( minutes != 0 ) prettyDurations.add( String.format( "%d minutes", minutes ) );
		if ( seconds != 0 ) prettyDurations.add( String.format( "%d seconds", seconds ) );

		// Combine all strings in the array & return it
		return String.join( ", ", prettyDurations );

	}

	/*public static void broadcastDiscordMessage( String author, String content ) {
		Text a = Text.literal( "(Discord) " ).setStyle( Style.EMPTY.withColor( TextColor.parse( "blue" ) ) );
		Text b = Text.literal( author ).setStyle( Style.EMPTY.withColor( TextColor.parse( "green" ) ) );
		Text c = Text.literal( String.format( ": %s", content ) ).setStyle( Style.EMPTY.withColor( TextColor.parse( "white" ) ) );
		Text message = Text.literal( "" ).append( a ).append( b ).append( c );
		LOGGER.debug( message.getString() );

		minecraftServer.getPlayerManager().broadcast( message, MessageType.SYSTEM );
		//minecraftServer.getPlayerManager().broadcast( Text.of( String.format( "%s: %s", author, content ) ), MessageType.SYSTEM, Util.NIL_UUID );
	}*/

	/*public static String capitalise( String original ) {
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
