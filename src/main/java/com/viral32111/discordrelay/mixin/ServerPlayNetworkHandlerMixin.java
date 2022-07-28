package com.viral32111.discordrelay.mixin;

import com.google.gson.JsonObject;
import com.viral32111.discordrelay.Config;
import com.viral32111.discordrelay.Utilities;
import com.viral32111.discordrelay.discord.API;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin( ServerPlayNetworkHandler.class )
@SuppressWarnings( "unused" ) // Mixins are used, the IDE just doesn't know it
public class ServerPlayNetworkHandlerMixin {

	// The player these events are for
	@Shadow public ServerPlayerEntity player;

	@Inject( method = "onChatMessage", at = @At( "HEAD" ) )
	private void handleMessage( ChatMessageC2SPacket packet, CallbackInfo callbackInfo ) {

		// Store the content of the message
		String messageContent = packet.chatMessage();

		// Display message in the console
		Utilities.Log( "Relaying chat message '{}' for player '{}'.", messageContent, player.getName().getString() );

		// Create payload for the chat message
		JsonObject chatPayload = new JsonObject();
		chatPayload.addProperty( "avatar_url", Config.Get( "external.face", Map.of( "uuid", player.getUuidAsString() ) ) );
		chatPayload.addProperty( "username", Utilities.GetPlayerName( player ) );
		chatPayload.addProperty( "content", messageContent ); // TODO: Escape markdown
		chatPayload.add( "allowed_mentions", API.AllowedMentions );

		// Send the chat message to the relay channel
		API.ExecuteWebhook( Config.Get( "discord.webhook.relay", null ), chatPayload, false );

	}

	/*private void onCommandExecute( String command, @Nullable Entity executor ) {
		if ( executor != null && executor.isPlayer() ) {
			ServerPlayerEntity player = ( ServerPlayerEntity ) executor;

			// Send message to logs
			JsonObject logsEmbedFieldPlayer = new JsonObject();
			logsEmbedFieldPlayer.addProperty( "name", "Executor" );
			logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Objects.requireNonNull( Config.Get( "external.profile" ) ), player.getUuidAsString() ) ) );
			logsEmbedFieldPlayer.addProperty( "inline", true );

			JsonObject logsEmbedFieldCommand = new JsonObject();
			logsEmbedFieldCommand.addProperty( "name", "Command" );
			logsEmbedFieldCommand.addProperty( "value", String.format( "`%s`", command ) );
			logsEmbedFieldCommand.addProperty( "inline", true );

			JsonArray logsEmbedFields = new JsonArray();
			logsEmbedFields.add( logsEmbedFieldPlayer );
			logsEmbedFields.add( logsEmbedFieldCommand );

			JsonObject logsEmbedThumbnail = new JsonObject();
			logsEmbedThumbnail.addProperty( "url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );

			JsonObject logsEmbedFooter = new JsonObject();
			logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Objects.requireNonNull( Config.Get( "log-date-format" ) ) ) );

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

			API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.log" ) ), logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
				if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
			} );
		}
	}*/

}
