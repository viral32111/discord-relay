package com.viral32111.discordrelay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class DiscordRelay implements DedicatedServerModInitializer {
	public static final HttpClient httpClient = HttpClient.newHttpClient();
	public static final Logger logger = LogManager.getLogger();
	private final JsonObject allowedMentions = new JsonObject();
	private static MinecraftServer minecraftServer; // TODO: Find a better solution

	// TODO: Implement proper rate-limiting & make this queued
	private void updateCategoryStatus( String status ) {
		JsonObject payload = new JsonObject();
		payload.addProperty( "name", String.format( Config.categoryFormat, status ) );

		Utilities.sendHttpRequest( "PATCH", URI.create( "https://discord.com/api/v9/channels/".concat( Config.categoryID ) ), payload.toString(), new HashMap<>() {{
			put( "Content-Type", "application/json" );
			put( "Authorization", String.format( "Bot %s", Config.botToken ) );
		}}  ).thenAccept( ( HttpResponse<String> response ) -> {
			if  ( response.statusCode() == 429 ) {
				JsonObject error = JsonParser.parseString( response.body() ).getAsJsonObject();
				logger.error( "Hit rate-limit when updating category status! (retry in {} seconds)", error.get( "retry_after" ).getAsDouble() );
			} else if ( response.statusCode() >= 400 ) {
				logger.error( "Got bad response when updating category status! ({}: {})", response.statusCode(), response.body() );
			}
		} );
	}

	/*public static int executeServerCommand( String command ) {
		return minecraftServer.getCommandManager().execute( minecraftServer.getCommandSource(), command );
	}*/

	public static void broadcastDiscordMessage( String author, String content ) {
		Text a = new LiteralText( "(Discord) " ).setStyle( Style.EMPTY.withColor( TextColor.parse( "blue" ) ) );
		Text b = new LiteralText( author ).setStyle( Style.EMPTY.withColor( TextColor.parse( "green" ) ) );
		Text c = new LiteralText( String.format( ": %s", content ) ).setStyle( Style.EMPTY.withColor( TextColor.parse( "white" ) ) );
		Text message = new LiteralText( "" ).append( a ).append( b ).append( c );
		logger.info( message.getString() );

		minecraftServer.getPlayerManager().broadcast( message, MessageType.SYSTEM, Util.NIL_UUID );
		//minecraftServer.getPlayerManager().broadcast( Text.of( String.format( "%s: %s", author, content ) ), MessageType.SYSTEM, Util.NIL_UUID );
	}

	@Override
	public void onInitializeServer() {

		// Send message to console
		logger.info( "I've initialised on the server!" );

		// Prevent all Discord mentions
		allowedMentions.add( "parse", new JsonArray() );

		// Load (or create) the configuration file
		try {
			Config.load();
		} catch ( IOException exception ) {
			logger.error( "Failed to load configuration file! ({})", exception.getMessage() );
		}

		// Register custom events
		PlayerConnect.EVENT.register( this::onPlayerConnect );
		PlayerDisconnect.EVENT.register( this::onPlayerDisconnect );
		PlayerChat.EVENT.register( this::onPlayerChat );
		PlayerAdvancement.EVENT.register( this::onPlayerAdvancement );
		PlayerDeath.EVENT.register( this::onPlayerDeath );
		CommandExecute.EVENT.register( this::onCommandExecute );

		// Register Fabric API events
		ServerLifecycleEvents.SERVER_STARTED.register( this::onServerStarted );
		ServerLifecycleEvents.SERVER_STOPPING.register( this::onServerStopping );

		// Asynchronously start the gateway bot
		CompletableFuture.runAsync( () -> {
			try {
				HttpResponse<String> response = Utilities.sendHttpRequest( "GET", URI.create( "https://discord.com/api/v9/gateway/bot" ), "", new HashMap<>() {{
					put( "Authorization", String.format( "Bot %s", Config.botToken ) );
				}} ).get();

				if ( response.statusCode() >= 400 ) throw new Exception( String.format( "Failed to fetch bot gateway! (%d: %s)", response.statusCode(), response.body() ) );

				JsonObject gatewayDetails = JsonParser.parseString( response.body() ).getAsJsonObject();
				URI gatewayUri = URI.create( gatewayDetails.get( "url" ).getAsString().concat( "/?encoding=json&v=9" ) );

				WebSocket.Builder builder = httpClient.newWebSocketBuilder();
				builder.header( "User-Agent", Config.httpUserAgent );
				builder.header( "From", Config.httpFrom );
				builder.buildAsync( gatewayUri, new Gateway() ).get();
			} catch ( Exception exception ) {
				logger.error( "Failed to start gateway bot! ({})", exception.getMessage() );
			}
		} );
	}

	// Events
	private void onServerStarted( MinecraftServer server ) {
		// Send message to console
		logger.info( "Relaying server started message..." );

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

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldAddress = new JsonObject();
		logsEmbedFieldAddress.addProperty( "name", "Address" );
		logsEmbedFieldAddress.addProperty( "value", String.format( "`%s`", Config.serverAddress ) );
		logsEmbedFieldAddress.addProperty( "inline", true );

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldAddress );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

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

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		updateCategoryStatus( "Empty" );
	}

	private void onServerStopping( MinecraftServer server ) {
		// Send message to console
		logger.info( "Relaying server stopped message..." );

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

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Server Stopped" );
		logsEmbed.addProperty( "color", 0xFFB200 );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		updateCategoryStatus( "Offline" );
	}

	private void onPlayerConnect( ServerPlayerEntity player, ClientConnection connection ) {
		// Send message to the console
		logger.info( "Relaying player '{}' join message...", player.getName().asString() );

		// Send message to relay
		JsonObject relayAuthor = new JsonObject();
		relayAuthor.addProperty( "name", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ).concat( " joined!" ) );
		relayAuthor.addProperty( "url", String.format( Config.profileURL, player.getUuidAsString() ) );
		relayAuthor.addProperty( "icon_url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.add( "author", relayAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Config.profileURL, player.getUuidAsString() ) ) );
		logsEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logsEmbedFieldAddress = new JsonObject();
		logsEmbedFieldAddress.addProperty( "name", "Address" );
		logsEmbedFieldAddress.addProperty( "value", String.format( "`%s`", connection.getAddress().toString().substring( 1 ) ) );
		logsEmbedFieldAddress.addProperty( "inline", true );

		// TODO: A field for IP lookup (Country, Is VPN/Proxy?)

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldPlayer );
		logsEmbedFields.add( logsEmbedFieldAddress );

		JsonObject logsEmbedThumbnail = new JsonObject();
		logsEmbedThumbnail.addProperty( "url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Player Joined" );
		logsEmbed.addProperty( "color", 0xED57EA );
		logsEmbed.add( "thumbnail", logsEmbedThumbnail );
		logsEmbed.add( "fields", logsEmbedFields );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		updateCategoryStatus( minecraftServer.getCurrentPlayerCount() + " Playing" );
	}

	private void onPlayerDisconnect( ServerPlayerEntity player ) {
		if ( player.getServer() == null ) return;
		if ( player.getServer().isStopping() ) return;
		if ( player.getServer().isStopped() ) return;

		// Send message to console
		logger.info( "Relaying player '{}' leave message...", player.getName().asString() );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ).concat( " left." ) );
		relayEmbedAuthor.addProperty( "url", String.format( Config.profileURL, player.getUuidAsString() ) );
		relayEmbedAuthor.addProperty( "icon_url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.add( "author", relayEmbedAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Config.profileURL, player.getUuidAsString() ) ) );
		logsEmbedFieldPlayer.addProperty( "inline", true );

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldPlayer );

		JsonObject logsEmbedThumbnail = new JsonObject();
		logsEmbedThumbnail.addProperty( "url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Player Left" );
		logsEmbed.addProperty( "color", 0xED57EA );
		logsEmbed.add( "thumbnail", logsEmbedThumbnail );
		logsEmbed.add( "fields", logsEmbedFields );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Update the category
		int playerCount = minecraftServer.getCurrentPlayerCount();
		updateCategoryStatus( ( playerCount > 0 ? String.format( "%d Playing", playerCount ) : "Empty" ) );
	}

	private void onPlayerChat( ServerPlayerEntity player, String message ) {
		// Send message to console
		logger.info( "Relaying player '{}' chat message '{}'...", player.getName().asString(), message );

		// Send message to relay
		JsonObject relayPayload = new JsonObject();
		relayPayload.addProperty( "avatar_url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );
		relayPayload.addProperty( "username", Utilities.getPlayerName( player ) );
		relayPayload.addProperty( "content", Utilities.escapeDiscordMarkdown( message ) );
		relayPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );
	}

	private void onPlayerAdvancement( ServerPlayerEntity player, String name, String criterion, String type ) {
		// Send message to console
		logger.info( "Relaying player '{}' leave message...", player.getName().asString() );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "url", String.format( Config.profileURL, player.getUuidAsString() ) );
		relayEmbedAuthor.addProperty( "icon_url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		switch ( type ) {
			case "challenge" -> relayEmbedAuthor.addProperty( "name", String.format( "%s completed the challenge %s!", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), name ) );
			case "task" -> relayEmbedAuthor.addProperty( "name", String.format( "%s has made the advancement %s!", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), name ) );
			case "goal" -> relayEmbedAuthor.addProperty( "name", String.format( "%s reached the goal %s!", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), name ) );
		}

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0x43F0F9 );
		relayEmbed.add( "author", relayEmbedAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Config.profileURL, player.getUuidAsString() ) ) );
		logsEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logsEmbedFieldName = new JsonObject();
		logsEmbedFieldName.addProperty( "name", "Name" );
		logsEmbedFieldName.addProperty( "value", name );
		logsEmbedFieldName.addProperty( "inline", true );

		JsonObject logsEmbedFieldCriterion = new JsonObject();
		logsEmbedFieldCriterion.addProperty( "name", "Criterion" );
		logsEmbedFieldCriterion.addProperty( "value", String.format( "`%s`", criterion ) );
		logsEmbedFieldCriterion.addProperty( "inline", true );

		JsonObject logsEmbedFieldType = new JsonObject();
		logsEmbedFieldType.addProperty( "name", "Type" );
		logsEmbedFieldType.addProperty( "value", Utilities.capitalise( type ) );
		logsEmbedFieldType.addProperty( "inline", true );

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldPlayer );
		logsEmbedFields.add( logsEmbedFieldName );
		logsEmbedFields.add( logsEmbedFieldCriterion );
		logsEmbedFields.add( logsEmbedFieldType );

		JsonObject logsEmbedThumbnail = new JsonObject();
		logsEmbedThumbnail.addProperty( "url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Player Advancement" );
		logsEmbed.addProperty( "color", 0xFFFFFF ); // TODO: Pick better color
		logsEmbed.add( "thumbnail", logsEmbedThumbnail );
		logsEmbed.add( "fields", logsEmbedFields );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );
	}

	private void onPlayerDeath( ServerPlayerEntity player, DamageSource damageSource ) {
		// Send message to console
		logger.info( "Relaying player '{}' death message...", player.getName().asString() );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", Utilities.getPlayerName( player ).concat( " died." ) );
		relayEmbedAuthor.addProperty( "url", String.format( Config.profileURL, player.getUuidAsString() ) );
		relayEmbedAuthor.addProperty( "icon_url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "description", Utilities.escapeDiscordMarkdown( damageSource.getDeathMessage( player ).getString() ).concat( "." ) );
		relayEmbed.addProperty( "color", 0xFCBA3F );
		relayEmbed.add( "author", relayEmbedAuthor );

		JsonArray relayEmbeds = new JsonArray();
		relayEmbeds.add( relayEmbed );

		JsonObject relayPayload = new JsonObject();
		relayPayload.add( "embeds", relayEmbeds );
		relayPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.relayWebhook, relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Config.profileURL, player.getUuidAsString() ) ) );
		logsEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logsEmbedFieldAttacker = new JsonObject();
		logsEmbedFieldAttacker.addProperty( "name", "Attacker" );
		logsEmbedFieldAttacker.addProperty( "value", ( damageSource.getAttacker() != null ? Utilities.escapeDiscordMarkdown( damageSource.getAttacker().getName().asString() ) : "N/A" ) );
		logsEmbedFieldAttacker.addProperty( "inline", true );

		JsonObject logsEmbedFieldReason = new JsonObject();
		logsEmbedFieldReason.addProperty( "name", "Reason" );
		logsEmbedFieldReason.addProperty( "value", Utilities.escapeDiscordMarkdown( damageSource.getDeathMessage( player ).getString() ) );
		logsEmbedFieldReason.addProperty( "inline", true );

		JsonArray logsEmbedFields = new JsonArray();
		logsEmbedFields.add( logsEmbedFieldPlayer );
		logsEmbedFields.add( logsEmbedFieldAttacker );
		logsEmbedFields.add( logsEmbedFieldReason );

		JsonObject logsEmbedThumbnail = new JsonObject();
		logsEmbedThumbnail.addProperty( "url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

		JsonObject logsEmbed = new JsonObject();
		logsEmbed.addProperty( "title", "Player Death" );
		logsEmbed.addProperty( "color", 0xFFFFFF ); // TODO: Pick better color
		logsEmbed.add( "thumbnail", logsEmbedThumbnail );
		logsEmbed.add( "fields", logsEmbedFields );
		logsEmbed.add( "footer", logsEmbedFooter );

		JsonArray logsEmbeds = new JsonArray();
		logsEmbeds.add( logsEmbed );

		JsonObject logsPayload = new JsonObject();
		logsPayload.add( "embeds", logsEmbeds );
		logsPayload.add( "allowed_mentions", allowedMentions );

		Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );
	}

	private void onCommandExecute( String command, @Nullable Entity executor ) {
		if ( executor != null && executor.isPlayer() ) {
			ServerPlayerEntity player = ( ServerPlayerEntity ) executor;

			// Send message to logs
			JsonObject logsEmbedFieldPlayer = new JsonObject();
			logsEmbedFieldPlayer.addProperty( "name", "Executor" );
			logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Config.profileURL, player.getUuidAsString() ) ) );
			logsEmbedFieldPlayer.addProperty( "inline", true );

			JsonObject logsEmbedFieldCommand = new JsonObject();
			logsEmbedFieldCommand.addProperty( "name", "Command" );
			logsEmbedFieldCommand.addProperty( "value", String.format( "`%s`", command ) );
			logsEmbedFieldCommand.addProperty( "inline", true );

			JsonArray logsEmbedFields = new JsonArray();
			logsEmbedFields.add( logsEmbedFieldPlayer );
			logsEmbedFields.add( logsEmbedFieldCommand );

			JsonObject logsEmbedThumbnail = new JsonObject();
			logsEmbedThumbnail.addProperty( "url", String.format( Config.skinAvatarURL, player.getUuidAsString() ) );

			JsonObject logsEmbedFooter = new JsonObject();
			logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Config.logsDateFormat ) );

			JsonObject logsEmbed = new JsonObject();
			logsEmbed.addProperty( "title", "Command Executed" );
			logsEmbed.addProperty( "color", 0xFFFFFF ); // TODO: Pick better color
			logsEmbed.add( "thumbnail", logsEmbedThumbnail );
			logsEmbed.add( "fields", logsEmbedFields );
			logsEmbed.add( "footer", logsEmbedFooter );

			JsonArray logsEmbeds = new JsonArray();
			logsEmbeds.add( logsEmbed );

			JsonObject logsPayload = new JsonObject();
			logsPayload.add( "embeds", logsEmbeds );
			logsPayload.add( "allowed_mentions", allowedMentions );

			Utilities.sendToWebhook( Config.logsWebhook, logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
				if ( response.statusCode() >= 400 ) logger.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
			} );
		}
	}
}
