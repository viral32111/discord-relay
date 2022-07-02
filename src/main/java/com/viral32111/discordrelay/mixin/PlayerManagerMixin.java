package com.viral32111.discordrelay.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mixin( PlayerManager.class )
@SuppressWarnings( "unused" ) // Mixins are used, the IDE just doesn't know it
public class PlayerManagerMixin {

	// Holds player IP addresses to use in the leave log embed
	private static final HashMap<UUID, String> playerAddresses = new HashMap<>();

	// Runs when a player joins the server
	@Inject( method = "onPlayerConnect", at = @At( "TAIL" )  )
	private void onPlayerConnect( ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo ) {

		// Add their IP address to the dictionary for later use
		String playerAddress = connection.getAddress().toString().substring( 1 ); // Substring removes the leading slash
		playerAddresses.put( player.getUuid(), playerAddress  );

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
		JsonObject logEmbedFieldPlayer = new JsonObject();
		logEmbedFieldPlayer.addProperty( "name", "Player" );
		logEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.GetPlayerName( player ), Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) ) );
		logEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logEmbedFieldAddress = new JsonObject();
		logEmbedFieldAddress.addProperty( "name", "Address" );
		logEmbedFieldAddress.addProperty( "value", String.format( "`%s`", playerAddress ) );
		logEmbedFieldAddress.addProperty( "inline", true );

		// TODO: Field for IP address lookup (e.g. country, is VPN/proxy?)

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedFieldPlayer );
		logEmbedFields.add( logEmbedFieldAddress );

		JsonObject logEmbedThumbnail = new JsonObject();
		logEmbedThumbnail.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.CurrentDateTime() );

		JsonObject logEmbed = new JsonObject();
		logEmbed.addProperty( "title", "Player Joined" );
		logEmbed.addProperty( "color", 0xED57EA );
		logEmbed.add( "fields", logEmbedFields );
		logEmbed.add( "thumbnail", logEmbedThumbnail );
		logEmbed.add( "footer", logsEmbedFooter );

		// Send the relay & log messages as embeds
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );
		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true );

		// Update the name of the category with the new number of active players
		Utilities.UpdateCategoryStatus( String.format( "%d Playing", Objects.requireNonNull( player.getServer() ).getCurrentPlayerCount() ) );

	}

	// Runs when a player leaves the server
	@Inject( method = "remove", at = @At( "TAIL" ) )
	private void remove( ServerPlayerEntity player, CallbackInfo callbackInfo ) {

		// Display message in the console
		Utilities.Log( "Relaying leave message for player '{}'.", player.getName().getString() );

		// Do not continue if the server is closing
		if ( player.getServer() == null || player.getServer().isStopping() || player.getServer().isStopped() ) return;

		// Create an embed for the relay message
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", String.format( "%s left.", Utilities.GetPlayerName( player ) ) );
		relayEmbedAuthor.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );
		relayEmbedAuthor.addProperty( "icon_url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.add( "author", relayEmbedAuthor );

		// Create an embed for the log message
		JsonObject logEmbedFieldPlayer = new JsonObject();
		logEmbedFieldPlayer.addProperty( "name", "Player" );
		logEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.GetPlayerName( player ), Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) ) );
		logEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logEmbedFieldAddress = new JsonObject();
		logEmbedFieldAddress.addProperty( "name", "Address" );
		logEmbedFieldAddress.addProperty( "value", String.format( "`%s`", playerAddresses.getOrDefault( player.getUuid(), "Unknown" ) ) ); // Get the player's IP address from the dictionary
		logEmbedFieldAddress.addProperty( "inline", true );

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedFieldPlayer );
		logEmbedFields.add( logEmbedFieldAddress );

		JsonObject logEmbedThumbnail = new JsonObject();
		logEmbedThumbnail.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );

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
		int playerCount = Objects.requireNonNull( player.getServer() ).getCurrentPlayerCount();
		Utilities.UpdateCategoryStatus( ( playerCount > 0 ? String.format( "%d Playing", playerCount ) : "Empty" ) );

		// Remove the player's IP address from the dictionary
		playerAddresses.remove( player.getUuid() );

	}

}
