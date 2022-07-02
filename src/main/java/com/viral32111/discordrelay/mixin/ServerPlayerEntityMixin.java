package com.viral32111.discordrelay.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin( ServerPlayerEntity.class )
public class ServerPlayerEntityMixin {
	/*@Inject( method = "onDeath", at = @At( "TAIL" ) )
	private void onDeath( DamageSource damageSource, CallbackInfo info ) {
		PlayerDeath.EVENT.invoker().interact( ( ServerPlayerEntity ) ( Object ) this, damageSource );
	}*/

	/*
	// Send message to console
		Utilities.Log( "Relaying player '{}' death message...", player.getName().getString() );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "name", Utilities.getPlayerName( player ).concat( " died." ) );
		relayEmbedAuthor.addProperty( "url", String.format( Objects.requireNonNull( Config.Get( "external.profile" ) ), player.getUuidAsString() ) );
		relayEmbedAuthor.addProperty( "icon_url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );

		JsonObject relayEmbed = new JsonObject();
		relayEmbed.addProperty( "description", Utilities.escapeDiscordMarkdown( damageSource.getDeathMessage( player ).getString() ).concat( "." ) );
		relayEmbed.addProperty( "color", 0xFCBA3F );
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
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Objects.requireNonNull( Config.Get( "external.profile" ) ), player.getUuidAsString() ) ) );
		logsEmbedFieldPlayer.addProperty( "inline", true );

		JsonObject logsEmbedFieldAttacker = new JsonObject();
		logsEmbedFieldAttacker.addProperty( "name", "Attacker" );
		logsEmbedFieldAttacker.addProperty( "value", ( damageSource.getAttacker() != null ? Utilities.escapeDiscordMarkdown( damageSource.getAttacker().getName().getString() ) : "N/A" ) );
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
		logsEmbedThumbnail.addProperty( "url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Objects.requireNonNull( Config.Get( "log-date-format" ) ) ) );

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

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.log" ) ), logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );
	 */
}
