package com.viral32111.discordrelay;

import com.viral32111.discordrelay.discord.Gateway;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

// The main entry point class, only runs on a server environment
public class DiscordRelay implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {

		// Display message in the console
		Utilities.Log( "Discord Relay has initialized." );

		// Load the configuration file
		try {
			Config.Load();
		} catch ( IOException exception ) {
			Utilities.LOGGER.error( "Failed to load the configuration file! ({})", exception.getMessage() );
			return; // Do not continue
		}

		// Start the Discord gateway client to listen for Discord -> Minecraft messages
		Gateway.Start();

		// Register events from the Fabric API
		// https://maven.fabricmc.net/docs/fabric-api-0.57.1+1.19.1/net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.html
		ServerLifecycleEvents.SERVER_STARTED.register( this::onServerStarted );
		ServerLifecycleEvents.SERVER_STOPPING.register( this::onServerStopping );

	}

	// Runs when the server finishes loading
	private void onServerStarted( MinecraftServer server ) {

		// Send message to console
		/*Utilities.Log( "Relaying server started message..." );

		// Update global server property
		minecraftServer = server;

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", "The server is now open!" );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0x00FF00 );
		relayEmbed.add( "author", relayEmbedAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.relay" ) ), relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldAddress = new JsonObject();
		logsEmbedFieldAddress.addProperty( "name", "Address" );
		logsEmbedFieldAddress.addProperty( "value", String.format( "`%s`", Objects.requireNonNull( Config.Get( "public-server-address" ) ) ) );
		logsEmbedFieldAddress.addProperty( "inline", true );

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldAddress );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Objects.requireNonNull( Config.Get( "log-date-format" ) ) ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Server Started" );
		logsEmbed.addProperty( "color", 0xFFB200 );
		logsEmbed.add( "fields", logsEmbedFields );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.log" ) ), logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		updateCategoryStatus( "Empty" );*/

	}

	// Runs when the server is about to close
	private void onServerStopping( MinecraftServer server ) {

		// Send message to console
		/*Utilities.Log( "Relaying server stopped message..." );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", "The server has closed." );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFF0000 );
		relayEmbed.add( "author", relayEmbedAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.relay" ) ), relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Objects.requireNonNull( Config.Get( "discord.webhook.relay" ) ) ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Server Stopped" );
		logsEmbed.addProperty( "color", 0xFFB200 );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.log" ) ), logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		updateCategoryStatus( "Offline" );*/

	}

}
