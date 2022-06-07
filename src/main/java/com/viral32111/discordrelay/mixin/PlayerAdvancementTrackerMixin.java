package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.PlayerAdvancement;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( PlayerAdvancementTracker.class )
public class PlayerAdvancementTrackerMixin {
	@Shadow
	public ServerPlayerEntity owner;

	@Inject( method = "grantCriterion", at = @At( value = "INVOKE", target = "Lnet/minecraft/advancement/PlayerAdvancementTracker;updateDisplay(Lnet/minecraft/advancement/Advancement;)V" ) )
	private void grantCriterion( Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> info ) {
		if ( advancement == null ) return;
		if ( advancement.getDisplay() == null ) return;
		if ( !advancement.getDisplay().shouldAnnounceToChat() ) return;
		//if ( !owner.world.getGameRules().getBoolean( GameRules.ANNOUNCE_ADVANCEMENTS ) ) return;

		PlayerAdvancement.EVENT.invoker().interact( owner, advancement.getDisplay().getTitle().getString(), criterionName, advancement.getDisplay().getFrame().getId() );
	}
}
