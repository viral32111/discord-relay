package com.viral32111.discordrelay.discord

import io.ktor.http.*

class Gateway( private val webSocketUrl: Url ) {
	fun connect() {
		throw NotImplementedError()
	}

	fun disconnect() {
		throw NotImplementedError()
	}

	private fun heartbeat() {
		throw NotImplementedError()
	}
}