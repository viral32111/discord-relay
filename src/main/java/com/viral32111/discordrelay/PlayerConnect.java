package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerConnect {
	void interact( ServerPlayerEntity player, ClientConnection connection );

	Event<PlayerConnect> EVENT = EventFactory.createArrayBacked( PlayerConnect.class, ( listeners ) -> ( player, connection ) -> {
		for ( PlayerConnect listener : listeners )  listener.interact( player, connection );
	} );
}
