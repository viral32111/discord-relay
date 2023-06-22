package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/channel#embed-object

@Serializable
data class Embed(
	@SerialName( "title" ) val title: String?,
	@SerialName( "description" ) val description: String?,
	@SerialName( "url" ) val url: String?,
	@SerialName( "timestamp" ) val timestamp: String?,
	@SerialName( "color" ) val color: Int?,
	@SerialName( "footer" ) val footer: EmbedFooter?,
	@SerialName( "thumbnail" ) val thumbnail: EmbedThumbnail?,
	@SerialName( "author" ) val author: EmbedAuthor?,
	@SerialName( "fields" ) val fields: List<EmbedField>?
)

@Serializable
data class EmbedFooter(
	@Required @SerialName( "text" ) val text: String,
	@Required @SerialName( "icon_url" ) val iconUrl: String
)

@Serializable
data class EmbedThumbnail(
	@Required @SerialName( "url" ) val name: String
)

@Serializable
data class EmbedAuthor(
	@Required @SerialName( "name" ) val name: String,
	@SerialName( "url" ) val url: String?,
	@SerialName( "icon_url" ) val iconUrl: String?
)

@Serializable
data class EmbedField(
	@Required @SerialName( "name" ) val name: String,
	@Required @SerialName( "value" ) val value: String,
	@SerialName( "inline" ) val inline: Boolean?
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
	var fields: MutableList<EmbedField>? = null
) {
	fun build() = Embed( title, description, url, timestamp, color, footer, thumbnail, author, fields )
}

fun createEmbed( block: EmbedBuilder.() -> Unit ) = EmbedBuilder().apply( block ).build()



data class EmbedAuthorBuilder(
	var name: String,
	var url: String? = null,
	var iconUrl: String? = null
) {
	fun build() = EmbedAuthor( name, url, iconUrl )
}

fun createEmbedAuthor( name: String, block: EmbedAuthorBuilder.() -> Unit ) = EmbedAuthorBuilder( name ).apply( block ).build()
