package com.viral32111.discordrelay.mixin;

import com.viral32111.discordrelay.PlayerConnect;
import com.viral32111.discordrelay.PlayerDisconnect;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( PlayerManager.class )
public class PlayerManagerMixin {
	@Inject( method = "onPlayerConnect", at = @At( "TAIL" ) )
	private void onPlayerConnect( ClientConnection connection, ServerPlayerEntity player, CallbackInfo info ) {
		 PlayerConnect.EVENT.invoker().interact( player, connection );
	}

	@Inject( method = "remove", at = @At( "TAIL" ) )
	public void remove( ServerPlayerEntity player, CallbackInfo info ) {
		PlayerDisconnect.EVENT.invoker().interact( player );
	}
}
