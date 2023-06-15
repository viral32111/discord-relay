package com.viral32111.discordrelay.helper

import com.viral32111.discordrelay.DiscordRelay
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.MinecraftVersion

object Version {

	/**
	 * Gets the version of a mod.
	 * @param modIdentifier The ID of the mod.
	 * @param fabricLoader An instance of the Fabric Loader.
	 * @since 0.6.0
	 * @return The version of the mod.
	 */
	private fun byModIdentifier( modIdentifier: String = DiscordRelay.MOD_ID, fabricLoader: FabricLoader = FabricLoader.getInstance() ) =
		fabricLoader.getModContainer( modIdentifier ).orElseThrow {
			throw IllegalStateException( "Mod container for ID '${ modIdentifier }' not found" )
		}.metadata.version.friendlyString

	// System
	fun java(): String = System.getProperty( "java.version" )

	// Game
	fun minecraft(): String = MinecraftVersion.CURRENT.id

	// Fabric
	fun fabricLoader(): String = byModIdentifier( "fabricloader" )

	// Mods
	fun fabricAPI(): String = byModIdentifier( "fabric-api" )
	fun fabricLanguageKotlin(): String = byModIdentifier( "fabric-language-kotlin" )
	fun events(): String = byModIdentifier( "events" )
	fun discordRelay(): String = byModIdentifier( "discordrelay" )

}
