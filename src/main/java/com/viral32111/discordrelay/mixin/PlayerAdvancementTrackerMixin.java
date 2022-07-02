package com.viral32111.discordrelay.mixin;

import net.minecraft.advancement.PlayerAdvancementTracker;
import org.spongepowered.asm.mixin.Mixin;

@Mixin( PlayerAdvancementTracker.class )
public class PlayerAdvancementTrackerMixin {
	/*@Shadow
	public ServerPlayerEntity owner;*/

	/*@Inject( method = "grantCriterion", at = @At( value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;updateDisplay(Lnet/minecraft/advancement/Advancement;)V" ) )
	private void grantCriterion( Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> info ) {
		if ( advancement == null ) return;
		if ( advancement.getDisplay() == null ) return;
		if ( !advancement.getDisplay().shouldAnnounceToChat() ) return;
		//if ( !owner.world.getGameRules().getBoolean( GameRules.ANNOUNCE_ADVANCEMENTS ) ) return;

		PlayerAdvancement.EVENT.invoker().interact( owner, advancement.getDisplay().getTitle().getString(), criterionName, advancement.getDisplay().getFrame().getId() );
	}*/

	/*
	// Send message to console
		Utilities.Log( "Relaying player '{}' leave message...", player.getName().getString() );

		// Send message to relay
		JsonObject relayEmbedAuthor = new JsonObject();
		relayEmbedAuthor.addProperty( "url", String.format( Objects.requireNonNull( Config.Get( "external.profile" ) ), player.getUuidAsString() ) );
		relayEmbedAuthor.addProperty( "icon_url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );

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

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.relay" ) ), relayPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );

		// Send message to logs
		JsonObject logsEmbedFieldPlayer = new JsonObject();
		logsEmbedFieldPlayer.addProperty( "name", "Player" );
		logsEmbedFieldPlayer.addProperty( "value", String.format( "[%s](%s)", Utilities.escapeDiscordMarkdown( Utilities.getPlayerName( player ) ), String.format( Objects.requireNonNull( Config.Get( "external.profile" ) ), player.getUuidAsString() ) ) );
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
		logsEmbedThumbnail.addProperty( "url", String.format( Objects.requireNonNull( Config.Get( "external.face" ) ), player.getUuidAsString() ) );

		JsonObject logsEmbedFooter = new JsonObject();
		logsEmbedFooter.addProperty( "text", Utilities.currentDateTime( Objects.requireNonNull( Config.Get( "log-date-format" ) ) ) );

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

		API.ExecuteWebhook( Objects.requireNonNull( Config.Get( "discord.webhook.log" ) ), logsPayload ).thenAccept( ( HttpResponse<String> response ) -> {
			if ( response.statusCode() >= 400 ) Utilities.LOGGER.error( "Got bad response when executing webhook! ({}: {})", response.statusCode(), response.body() );
		} );
	 */
}
