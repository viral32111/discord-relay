package com.viral32111.discordrelay

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World

fun ServerPlayerEntity.getDimensionName(): String =
	when ( val dimension = world.registryKey ) {
		World.OVERWORLD -> "Overworld"
		World.NETHER -> "The Nether"
		World.END -> "The End"
		else -> dimension.toString()
	}

fun ServerPlayerEntity.getNickName(): String? = displayName.string.takeUnless { it == name.string }
