package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/channel#embed-object

@Serializable
data class Embed(
	@SerialName( "title" ) val title: String? = null,
	@SerialName( "description" ) val description: String? = null,
	@SerialName( "url" ) val url: String? = null,
	@SerialName( "timestamp" ) val timestamp: String? = null,
	@SerialName( "color" ) val color: Int? = null,
	@SerialName( "footer" ) val footer: EmbedFooter? = null,
	@SerialName( "thumbnail" ) val thumbnail: EmbedThumbnail? = null,
	@SerialName( "author" ) val author: EmbedAuthor? = null,
	@SerialName( "fields" ) val fields: List<EmbedField>? = null
)

@Serializable
data class EmbedFooter(
	@Required @SerialName( "text" ) val text: String,
	@SerialName( "icon_url" ) val iconUrl: String? = null
)

@Serializable
data class EmbedThumbnail(
	@Required @SerialName( "url" ) val url: String
)

@Serializable
data class EmbedAuthor(
	@Required @SerialName( "name" ) val name: String,
	@SerialName( "url" ) val url: String? = null,
	@SerialName( "icon_url" ) val iconUrl: String? = null
)

@Serializable
data class EmbedField(
	@Required @SerialName( "name" ) val name: String,
	@Required @SerialName( "value" ) val value: String,
	@SerialName( "inline" ) val inline: Boolean? = null
)

data class EmbedBuilder(
	var title: String? = null,
	var description: String? = null,
	var url: String? = null,
	var timestamp: String? = null,
	var color: Int? = null,
	var footer: EmbedFooter? = null,
	var thumbnail: EmbedThumbnail? = null,
	var author: EmbedAuthor? = null,
	var fields: List<EmbedField>? = null
) {
	fun build() = Embed( title, description, url, timestamp, color, footer, thumbnail, author, fields )
}

fun createEmbed( block: EmbedBuilder.() -> Unit ) = EmbedBuilder().apply( block ).build()
