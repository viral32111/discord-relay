package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerDeath {
	void interact( ServerPlayerEntity player, DamageSource damageSource );

	Event<PlayerDeath> EVENT = EventFactory.createArrayBacked( PlayerDeath.class, ( listeners ) -> ( player, damageSource ) -> {
		for ( PlayerDeath listener : listeners ) listener.interact( player, damageSource );
	} );
}
