package com.viral32111.discordrelay.discord.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://discord.com/developers/docs/resources/webhook#execute-webhook

@Serializable
data class WebhookMessage(
	@SerialName( "avatar_url" ) val avatarUrl: String? = null,
	@SerialName( "username" ) val userName: String? = null,
	val content: String? = null,
	val embeds: MutableList<Embed>? = null,
	@SerialName( "allowed_mentions" ) val allowedMentions: AllowedMentions? = null
)

class WebhookMessageBuilder {
	var avatarUrl: String? = null
	var userName: String? = null
	var content: String = ""
	var embeds: MutableList<Embed>? = null
	var allowedMentions: AllowedMentions? = null

	fun preventMentions() { allowedMentions = AllowedMentions(
		parse = emptyList(),
		roles = emptyList(),
		users = emptyList(),
		repliedUser = false
	) }

	fun build() = WebhookMessage( avatarUrl, userName, content, embeds, allowedMentions )
}

fun createWebhookMessage( block: WebhookMessageBuilder.() -> Unit ) = WebhookMessageBuilder().apply( block ).build()
