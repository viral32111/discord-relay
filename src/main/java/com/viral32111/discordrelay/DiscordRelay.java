package com.viral32111.discordrelay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viral32111.discordrelay.discord.API;
import com.viral32111.discordrelay.discord.Gateway;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Objects;

// The main entry point class, only runs on a server environment
@SuppressWarnings( "unused" ) // This is used, the IDE just doesn't know it
public class DiscordRelay implements DedicatedServerModInitializer {

	@Override
	public void onInitializeServer() {

		// Display message in the console
		Utilities.Log( "Initialized." );

		// Do not parse any mentions
		API.AllowedMentions.add( "parse", new JsonArray() );

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

		// Display message in the console
		Utilities.Log( "Relaying server open message." );

		// Create an embed for the relay message
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", "The server is now open!" );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0x00FF00 );
		relayEmbed.add( "author", relayEmbedAuthor );

		// Create an embed for the log message
		JsonObject logEmbedFieldAddress = new JsonObject();
		logEmbedFieldAddress.addProperty( "name", "Address" );
		logEmbedFieldAddress.addProperty( "value", String.format( "`%s`", Objects.requireNonNull( Config.Get( "public-server-address", null ) ) ) );
		logEmbedFieldAddress.addProperty( "inline", true );

		JsonObject logEmbedFieldVersion = new JsonObject();
		logEmbedFieldVersion.addProperty( "name", "Version" );
		logEmbedFieldVersion.addProperty( "value", String.format( "`%s`", server.getVersion() ) );
		logEmbedFieldVersion.addProperty( "inline", true );

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedFieldAddress );
		logEmbedFields.add( logEmbedFieldVersion );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.CurrentDateTime() );

		JsonObject logEmbed = new JsonObject();
		logEmbed.addProperty( "title", "Server Started" );
		logEmbed.addProperty( "color", 0xFFB200 );
		logEmbed.add( "fields", logEmbedFields );
		logEmbed.add( "footer", logsEmbedFooter );

		// Send the relay & log messages as embeds
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );
		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true );

		// Update the name of the category to indicate the server is now open with no players online
		Utilities.UpdateCategoryStatus( "Empty", "Minecraft Server has started." );

	}

	// Runs when the server is about to close
	private void onServerStopping( MinecraftServer server ) {

		// Display message in the console
		Utilities.Log( "Relaying server closing message." );

		// Create an embed for the relay message
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", "The server has closed." );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFF0000 );
		relayEmbed.add( "author", relayEmbedAuthor );

		// Create an embed for the log message
		JsonObject logEmbedFieldAddress = new JsonObject();
		logEmbedFieldAddress.addProperty( "name", "Address" );
		logEmbedFieldAddress.addProperty( "value", String.format( "`%s`", Objects.requireNonNull( Config.Get( "public-server-address", null ) ) ) );
		logEmbedFieldAddress.addProperty( "inline", true );

		JsonObject logEmbedFieldVersion = new JsonObject();
		logEmbedFieldVersion.addProperty( "name", "Version" );
		logEmbedFieldVersion.addProperty( "value", String.format( "`%s`", server.getVersion() ) );
		logEmbedFieldVersion.addProperty( "inline", true );

		JsonArray logEmbedFields = new JsonArray();
		logEmbedFields.add( logEmbedFieldAddress );
		logEmbedFields.add( logEmbedFieldVersion );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.CurrentDateTime() );

		JsonObject logEmbed = new JsonObject();
		logEmbed.addProperty( "title", "Server Stopped" );
		logEmbed.addProperty( "color", 0xFFB200 );
		logEmbed.add( "fields", logEmbedFields );
		logEmbed.add( "footer", logsEmbedFooter );

		// Send the relay & log messages as embeds
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );
		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true );

		// Update the name of the category to indicate the server is closed
		Utilities.UpdateCategoryStatus( "Offline", "Minecraft Server has stopped." );

	}

}
