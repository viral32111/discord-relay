package com.viral32111.discordrelay;

import com.google.gson.JsonObject;
import com.viral32111.discordrelay.discord.API;
import net.minecraft.network.message.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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

	// An instance of the player manager of a Minecraft server, set in the main class
	public static PlayerManager playerManager = null;

	// Perform a HTTP request to a specified URL with a custom method, headers, and an optional body
	public static CompletableFuture<HttpResponse<String>> HttpRequest( String method, String url, Map<String, String> headers, @Nullable String body ) {

		Debug( "'{}' to '{}' with {} bytes", method, url, ( body != null ? body.length() : 0 ) );

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

	// Returns the current date & time in UTC using the configured format
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

	// Helpers to display messages in the console with a prefix to identify the message is from this mod
	public static void Log( String message, Object... params ) {
		LOGGER.info( String.format( "[Discord Relay] %s", message ), params );
	}
	public static void Debug( String message, Object... params ) {
		LOGGER.info( String.format( "[Discord Relay] %s", message ), params );
	}
	public static void Warn( String message, Object... params ) {
		LOGGER.warn( String.format( "[Discord Relay] %s", message ), params );
	}
	public static void Error( String message, Object... params ) {
		LOGGER.error( String.format( "[Discord Relay] %s", message ), params );
	}
	public static void Error( Throwable exception ) {
		LOGGER.error( String.format( "[Discord Relay] %s", exception.getMessage() ) );
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

	// Sends a Discord message in chat as a game message
	public static void BroadcastDiscordMessage( String authorName, String messageContent ) throws Exception {

		// Do not continue if the player manager has not been set yet
		if ( playerManager == null ) throw new Exception( "Player manager not initialised" );

		// Construct the styled chat message
		// TODO: Make the format of this configurable
		Text discordMessage = Text.literal( "" )
			.append( Text.literal( "(Discord) " ).setStyle( Style.EMPTY.withColor( TextColor.parse( "blue" ) ) ) )
			.append( Text.literal( authorName ).setStyle( Style.EMPTY.withColor( TextColor.parse( "white" ) ) ) )
			.append( Text.literal( String.format( ": %s", messageContent ) ).setStyle( Style.EMPTY.withColor( TextColor.parse( "white" ) ) ) );

		// Send the message in chat to all players
		// NOTE: System messages do not need to be signed
		playerManager.broadcast( discordMessage, MessageType.SYSTEM );

	}

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
