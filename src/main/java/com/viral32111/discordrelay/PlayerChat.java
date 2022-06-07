package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerChat {
	void interact( ServerPlayerEntity player, String message );

	Event<PlayerChat> EVENT = EventFactory.createArrayBacked( PlayerChat.class, ( listeners ) -> ( player, message ) -> {
		for ( PlayerChat listener : listeners ) listener.interact( player, message );
	} );
}
