package com.viral32111.discordrelay.discord

class Gateway( private val webSocketUrl: String ) {
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
