package com.viral32111.discordrelay;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface CommandExecute {
	void interact( String command, @Nullable Entity executor );

	Event<CommandExecute> EVENT = EventFactory.createArrayBacked( CommandExecute.class, ( listeners ) -> ( command, executor ) -> {
		for ( CommandExecute listener : listeners ) listener.interact( command, executor );
	} );
}
