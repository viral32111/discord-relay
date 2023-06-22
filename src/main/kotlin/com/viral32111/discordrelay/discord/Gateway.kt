package com.viral32111.discordrelay.discord

import java.net.URL

class Gateway( private val webSocketUrl: URL ) {
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
