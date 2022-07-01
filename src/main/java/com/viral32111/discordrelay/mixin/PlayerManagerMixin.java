package com.viral32111.discordrelay.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.DiscordRelay;
import com.viral32111.discordrelay.Utilities;
import com.viral32111.discordrelay.discord.API;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;

@Mixin( PlayerManager.class )
public class PlayerManagerMixin {

	@Inject( method = "onPlayerConnect", at = @At( "TAIL" )  )
	private void onPlayerConnect( ClientConnection connection, ServerPlayerEntity player, CallbackInfo callbackInfo ) {

		DiscordRelay.LOGGER.info( "Relaying join message for player '{}'.", player.getName().getString() );

		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", String.format( "%s joined!", Utilities.GetPlayerName( player ) ) );
		relayEmbedAuthor.addProperty( "url", Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) );
		relayEmbedAuthor.addProperty( "icon_url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "color", 0xFFFFFF );
		relayEmbed.add( "author", relayEmbedAuthor );

		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), relayEmbed, true );

		JsonObject logEmbedFieldPlayer = new JsonObject();
		logEmbedFieldPlayer.addProperty( "name", "Player" );
		logEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.GetPlayerName( player ), Config.Get( "external.profile", Map.of( "uuid", player.getUuidAsString() ) ) ) );
		logEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logEmbedFieldAddress = new JsonObject();
		logEmbedFieldAddress.addProperty( "name", "Address" );
		logEmbedFieldAddress.addProperty( "value", String.format( "`%s`", connection.getAddress().toString().substring( 1 ) ) );
		logEmbedFieldAddress.addProperty( "inline", true );

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

		API.ExecuteWebhook( Config.Get( "discord.webhook.log", null ), logEmbed, true );

		Utilities.UpdateCategoryStatus( Objects.requireNonNull( player.getServer() ).getCurrentPlayerCount() + " Playing" );

	}

	/*@Inject( method = "remove", at = @At( "TAIL" ) )
	private void remove( ServerPlayerEntity player, CallbackInfo callbackInfo ) {

	}*/

}
