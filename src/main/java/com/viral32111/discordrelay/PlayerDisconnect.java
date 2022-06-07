package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerDisconnect {
	void interact( ServerPlayerEntity player );

	Event<PlayerDisconnect> EVENT = EventFactory.createArrayBacked( PlayerDisconnect.class, ( listeners ) -> ( player ) -> {
		for ( PlayerDisconnect listener : listeners ) listener.interact( player );
	} );
}
