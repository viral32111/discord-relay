package com.viral32111.discordrelay.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;
import com.viral32111.discordrelay.discord.API;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mixin( PlayerManager.class )
@SuppressWarnings( "unused" ) // Mixins are used, the IDE just doesn't know it
public class PlayerManagerMixin {

	// Holds player IP addresses to use in the leave log embed
	private static final HashMap<UUID, String> playerAddresses = new HashMap<>();

	// Holds timestamps of when player's joined to work out their session playtime
	private static final HashMap<UUID, Long> playerJoinTimes = new HashMap<>();

	// Runs when a player joins the server
	@Inject( method = "onPlayerConnect", at = @At( "TAIL" )  )
	private void onPlayerConnect( ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo ) {

		// Cast the connection address to a class that provides more
		InetAddress connectionAddress = ( ( InetSocketAddress ) connection.getAddress() ).getAddress();

		// Add their IP address to the dictionary for later use
		String playerAddress = connectionAddress.getHostAddress();
		playerAddresses.put( player.getUuid(), playerAddress );

		// Store their join time in the dictionary for later use
		playerJoinTimes.put( player.getUuid(), System.currentTimeMillis() );

		// Display message in the console
		Utilities.Log( "Relaying join message for player '{}'.", player.getName().getString() );

		// Create an embed for the relay message
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", String.format( "%s joined!", Utilities.GetPlayerName( player ) ) );
		relayEmbedAuthor.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );
		relayEmbedAuthor.addProperty( "icon_url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.add( "author", relayEmbedAuthor );

		// Create an embed for the log message
		JsonObject logEmbedNameField = new JsonObject();
		logEmbedNameField.addProperty( "name", "Name" );
		logEmbedNameField.addProperty( "value", player.getName().getString() );
		logEmbedNameField.addProperty( "inline", true );

		JsonObject logEmbedNickField = new JsonObject();
		logEmbedNickField.addProperty( "name", "Nickname" );
		logEmbedNickField.addProperty( "value", ( Utilities.PlayerHasNickname( player ) ? player.getDisplayName().getString() : "*No nickname set.*" ) );
		logEmbedNickField.addProperty( "inline", true );

		JsonObject logEmbedAddressField = new JsonObject();
		logEmbedAddressField.addProperty( "name", "IP Address" );
		logEmbedAddressField.addProperty( "value", String.format( "`%s`", playerAddress ) );
		logEmbedAddressField.addProperty( "inline", true );

		JsonObject logEmbedLocationField = new JsonObject();
		logEmbedLocationField.addProperty( "name", "Location" );
		logEmbedLocationField.addProperty( "value", "London, United Kingdom" ); // TODO: This is an example
		logEmbedLocationField.addProperty( "inline", true );

		JsonObject logEmbedSpoofField = new JsonObject();
		logEmbedSpoofField.addProperty( "name", "Spoof" );
		logEmbedSpoofField.addProperty( "value", "VPN in use (50% risk)" ); // TODO: This is an example
		logEmbedSpoofField.addProperty( "inline", true );

		JsonObject logEmbedSeenField = new JsonObject();
		logEmbedSeenField.addProperty( "name", "Last Seen" );
		logEmbedSeenField.addProperty( "value", "3 hours ago (for 10 minutes)" ); // TODO: This is an example
		logEmbedSeenField.addProperty( "inline", false );

		JsonObject logEmbedIdentifierField = new JsonObject();
		logEmbedIdentifierField.addProperty( "name", "Unique Identifier (UUID)" );
		logEmbedIdentifierField.addProperty( "value", String.format( "[`%s`](%s)", player.getUuidAsString(), Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) ) );
		logEmbedIdentifierField.addProperty( "inline", false );

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedNameField );
		logEmbedFields.add( logEmbedNickField );
		logEmbedFields.add( logEmbedAddressField );
		logEmbedFields.add( logEmbedLocationField );
		logEmbedFields.add( logEmbedSpoofField );
		logEmbedFields.add( logEmbedSeenField );
		logEmbedFields.add( logEmbedIdentifierField );

		JsonObject logEmbedThumbnail = new JsonObject();
		logEmbedThumbnail.addProperty( "url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.CurrentDateTime() );

		JsonObject logEmbed = new JsonObject();
		logEmbed.addProperty( "title", "Player Joined" );
		logEmbed.addProperty( "color", 0xED57EA );
		logEmbed.add( "fields", logEmbedFields );
		logEmbed.add( "thumbnail", logEmbedThumbnail );
		logEmbed.add( "footer", logsEmbedFooter );

		// Send the relay message as an embed
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );

		// Send the log message as an embed, and after it has been created...
		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true ).thenAccept( ( String messageId ) -> {

			// Fetch useful information about the player's IP address (e.g. country, is VPN/proxy), but only if it is not a local address
			if ( connectionAddress.isAnyLocalAddress() ) Utilities.HttpRequest( "GET", String.format( "https://proxycheck.io/v2/%s?vpn=3&asn=1&node=0&time=0&risk=1&port=0&seen=1", playerAddress ), Map.of( "Accept", "application/json" ), null ).thenAccept( ( HttpResponse<String> response ) -> {

				// Error if the request was unsuccessful
				if ( response.statusCode() < 200 || response.statusCode() > 299 ) throw new RuntimeException( String.format( "Proxycheck API request unsuccessful with code: %d", response.statusCode() ) );

				// Store the content of the response, and error if it is empty
				String responseBody = response.body();
				if ( responseBody.length() <= 0 ) throw new RuntimeException( "Proxycheck API response has no body." );

				JsonObject proxycheckResult = JsonParser.parseString( responseBody ).getAsJsonObject().getAsJsonObject( playerAddress );
				String asNumber = proxycheckResult.get( "asn" ).getAsString();
				String serviceProvider = proxycheckResult.get( "provider" ).getAsString();
				String locationContinent = proxycheckResult.get( "continent" ).getAsString();
				String locationCountry = proxycheckResult.get( "country" ).getAsString();
				String locationCountryCode = proxycheckResult.get( "isocode" ).getAsString();
				String locationRegion = proxycheckResult.get( "region" ).getAsString();
				String locationRegionCode = proxycheckResult.get( "regioncode" ).getAsString();
				String locationCity = proxycheckResult.get( "city" ).getAsString();
				Double locationLatitude = proxycheckResult.get( "latitude" ).getAsDouble();
				Double locationLongitude = proxycheckResult.get( "longitude" ).getAsDouble();
				boolean isProxy = proxycheckResult.get( "proxy" ).getAsString().equals( "yes" );
				boolean isVPN = proxycheckResult.get( "vpn" ).getAsString().equals( "yes" );
				int riskPercentage = proxycheckResult.get( "risk" ).getAsInt();

				Utilities.Debug( "Internet Service Provider: {} ({})", serviceProvider, asNumber );
				Utilities.Debug( "Location: {}, {} ({}), {} ({}), {} [{}, {}]", locationCity, locationRegion, locationRegionCode, locationCountry, locationCountryCode, locationContinent, locationLatitude, locationLongitude );
				Utilities.Debug( "Proxy: {}, VPN: {} (Risk {}%)", isProxy, isVPN, riskPercentage );

				for ( JsonElement element : logEmbed.get( "fields" ).getAsJsonArray() ) {
					JsonObject field = element.getAsJsonObject();

					if ( field.get( "name" ).getAsString().equals( "Location" ) ) {
						field.addProperty( "value", String.format( "%s, %s", locationCity, locationCountry ) );

					} else if ( field.get( "name" ).getAsString().equals( "Spoof" ) ) {
						field.addProperty( "value", String.format( "VPN: %b, Proxy: %b (%d%% risk)", isVPN, isProxy, riskPercentage ) );
					}
				}

				JsonArray logEmbeds = new JsonArray();
				logEmbeds.add( logEmbed );

				JsonObject logEmbedsPayload = new JsonObject();
				logEmbedsPayload.add( "embeds", logEmbeds );
				logEmbedsPayload.add( "allowed_mentions", API.AllowedMentions );

				API.Request( "PATCH", String.format( "webhooks/%s/messages/%s", Config.Get( "discord.webhook.relay", null ), messageId ), logEmbedsPayload, null );

			} );

		} );

		// Update the name of the category with the new number of active players
		try {
			Utilities.UpdateCategoryStatus( String.format( "%d Playing", Objects.requireNonNull( player.getServer() ).getCurrentPlayerCount() ), "Player joined Minecraft Server." );
		} catch ( Exception exception ) {
			Utilities.LOGGER.error( exception.getMessage() );
		}
	}

	// Runs when a player leaves the server
	@Inject( method = "remove", at = @At( "TAIL" ) )
	private void remove( ServerPlayerEntity player, CallbackInfo callbackInfo ) {

		// Do not continue if the server is closing
		if ( player.getServer() == null || player.getServer().isStopping() || player.getServer().isStopped() ) return;

		// Display message in the console
		Utilities.Log( "Relaying leave message for player '{}'.", player.getName().getString() );

		// Get their IP address
		String ipAddress = "*Unknown*";
		if ( playerAddresses.containsKey( player.getUuid() ) ) {
			ipAddress = String.format( "`%s`", playerAddresses.get( player.getUuid() ) );
		}

		// Get their session playtime
		String sessionDuration = "*Unknown*";
		if ( playerJoinTimes.containsKey( player.getUuid() ) ) { // This should never evaluate to false
			sessionDuration = Utilities.ToPrettyDuration( System.currentTimeMillis() - playerJoinTimes.get( player.getUuid() ), TimeUnit.MILLISECONDS );
		}

		// Create an embed for the relay message
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", String.format( "%s left.", Utilities.GetPlayerName( player ) ) );
		relayEmbedAuthor.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );
		relayEmbedAuthor.addProperty( "icon_url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.addProperty( "description", String.format( "Played for %s.", sessionDuration ) );
		relayEmbed.add( "author", relayEmbedAuthor );

		// Create an embed for the log message
		JsonObject logEmbedNameField = new JsonObject();
		logEmbedNameField.addProperty( "name", "Name" );
		logEmbedNameField.addProperty( "value", player.getName().getString() );
		logEmbedNameField.addProperty( "inline", true );

		JsonObject logEmbedNickField = new JsonObject();
		logEmbedNickField.addProperty( "name", "Nickname" );
		logEmbedNickField.addProperty( "value", ( Utilities.PlayerHasNickname( player ) ? player.getDisplayName().getString() : "*No nickname set.*" ) );
		logEmbedNickField.addProperty( "inline", true );

		JsonObject logEmbedAddressField = new JsonObject();
		logEmbedAddressField.addProperty( "name", "IP Address" );
		logEmbedAddressField.addProperty( "value", ipAddress );
		logEmbedAddressField.addProperty( "inline", false );

		JsonObject logEmbedLocationField = new JsonObject();
		logEmbedLocationField.addProperty( "name", "Location" );
		logEmbedLocationField.addProperty( "value", "London, United Kingdom" ); // TODO: This is an example
		logEmbedLocationField.addProperty( "inline", true );

		JsonObject logEmbedSpoofField = new JsonObject();
		logEmbedSpoofField.addProperty( "name", "Spoof" );
		logEmbedSpoofField.addProperty( "value", "VPN in use (50% risk)" ); // TODO: This is an example
		logEmbedSpoofField.addProperty( "inline", true );

		JsonObject logEmbedDurationField = new JsonObject();
		logEmbedDurationField.addProperty( "name", "Session Duration" );
		logEmbedDurationField.addProperty( "value", sessionDuration );
		logEmbedDurationField.addProperty( "inline", true );

		JsonObject logEmbedIdentifierField = new JsonObject();
		logEmbedIdentifierField.addProperty( "name", "Unique Identifier (UUID)" );
		logEmbedIdentifierField.addProperty( "value", String.format( "[`%s`](%s)", player.getUuidAsString(), Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) ) );
		logEmbedIdentifierField.addProperty( "inline", false );

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedNameField );
		logEmbedFields.add( logEmbedNickField );
		logEmbedFields.add( logEmbedAddressField );
		logEmbedFields.add( logEmbedLocationField );
		logEmbedFields.add( logEmbedSpoofField );
		logEmbedFields.add( logEmbedDurationField );
		logEmbedFields.add( logEmbedIdentifierField );

		JsonObject logEmbedThumbnail = new JsonObject();
		logEmbedThumbnail.addProperty( "url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.CurrentDateTime() );

		JsonObject logEmbed = new JsonObject();
		logEmbed.addProperty( "title", "Player Left" );
		logEmbed.addProperty( "color", 0xED57EA );
		logEmbed.add( "fields", logEmbedFields );
		logEmbed.add( "thumbnail", logEmbedThumbnail );
		logEmbed.add( "footer", logsEmbedFooter );

		// Send the relay & log messages as embeds
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );
		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true );

		// Update the name of the category with the new number of active players, or none
		try {
			int playerCount = Objects.requireNonNull( player.getServer() ).getCurrentPlayerCount();
			Utilities.UpdateCategoryStatus( ( playerCount > 0 ? String.format( "%d Playing", playerCount ) : "Empty" ), "Player left Minecraft Server." );
		} catch ( Exception exception ) {
			Utilities.LOGGER.error( exception.getMessage() );
		}

		// Remove the player's IP address & login time from the dictionaries
		playerAddresses.remove( player.getUuid() );
		playerJoinTimes.remove( player.getUuid() );

	}

}
