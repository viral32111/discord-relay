package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerAdvancement {
	void interact( ServerPlayerEntity player, String name, String criterion, String type );

	Event<PlayerAdvancement> EVENT = EventFactory.createArrayBacked( PlayerAdvancement.class, ( listeners ) -> ( player, name, criterion, type ) -> {
		for ( PlayerAdvancement listener : listeners ) listener.interact( player, name, criterion, type );
	} );
}
