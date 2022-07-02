package com.viral32111.discordrelay.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin( ServerPlayNetworkHandler.class )
public class ServerPlayNetworkHandlerMixin {

	/*@Shadow
	public ServerPlayerEntity player;*/

	/*@Inject( method = "handleMessage", at = @At( "TAIL" ) )
	private void handleMessage( ChatMessageC2SPacket packet, FilteredMessage<String> message, CallbackInfo callbackInfo ) {
		String messageContent = message.raw();

		PlayerChat.EVENT.invoker().interact( player, messageContent );

		/*if ( messageContent.startsWith( "/" ) ) {
			// TODO: Command Blocks, Sign Commands, Console Commands
			CommandExecute.EVENT.invoker().interact( messageContent.substring( 1 ), player );
		} else {
			PlayerChat.EVENT.invoker().interact( player, message.getFiltered() );
		}*/

		// Send message to console
		/*Utilities.Log( "Relaying player '{}' chat message '{}'...", player.getName().getString(), message );

		// Send message to relay
		JsonObject relayPayload = new JsonObject();
		relayPayload.addProperty( "avatar_url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );
		relayPayload.addProperty( "username", Utilities.getPlayerName( player ) );
		relayPayload.addProperty( "content", Utilities.escapeDiscordMarkdown( message ) );
		relayPayload.add( "allowed_mentions", allowedMentions );

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.relay" ) ), relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

	}*/

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
