package com.viral32111.discordrelay.discord.builder

import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/channel#channel-object

@Serializable
data class Channel(
	val name: String? = null,
)

data class ChannelBuilder(
	var name: String? = null,
) {
	fun build() = Channel( name )
}

fun createChannel( block: ChannelBuilder.() -> Unit ) = ChannelBuilder().apply( block ).build()
