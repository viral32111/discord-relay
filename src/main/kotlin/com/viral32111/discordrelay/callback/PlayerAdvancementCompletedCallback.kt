package com.viral32111.discordrelay.callback

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.advancement.Advancement
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult

fun interface PlayerAdvancementCompletedCallback {
	companion object {
		val EVENT: Event<PlayerAdvancementCompletedCallback> = EventFactory.createArrayBacked( PlayerAdvancementCompletedCallback::class.java ) { listeners ->
			PlayerAdvancementCompletedCallback { player, advancement, criterionName, shouldAnnounceToChat ->
				for ( listener in listeners ) {
					val result = listener.interact( player, advancement, criterionName, shouldAnnounceToChat )
					if ( result != ActionResult.PASS ) return@PlayerAdvancementCompletedCallback result
				}

				return@PlayerAdvancementCompletedCallback ActionResult.PASS
			}
		}
	}

	fun interact( player: ServerPlayerEntity, advancement: Advancement, criterionName: String, shouldAnnounceToChat: Boolean ): ActionResult
}
