package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.callback.PlayerAdvancementCompletedCallback;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( PlayerAdvancementTracker.class )
public class PlayerAdvancementTrackerMixin {

	@Shadow
	private ServerPlayerEntity owner;

	@Shadow
	public AdvancementProgress getProgress( Advancement advancement ) { return null; }

	@Inject( method = "grantCriterion", at = @At( "RETURN" ) )
	private void grantCriterion( Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> info ) {

		AdvancementProgress advancementProgress = this.getProgress( advancement );
		if ( !advancementProgress.isDone() ) return;

		boolean shouldAnnounceToChat = ( advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceToChat() ) && owner.getWorld().getGameRules().getBoolean( GameRules.ANNOUNCE_ADVANCEMENTS );

		PlayerAdvancementCompletedCallback.Companion.getEVENT().invoker().interact( owner, advancement, criterionName, shouldAnnounceToChat );

	}

}
