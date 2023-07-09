package com.viral32111.discordrelay

import java.net.http.WebSocket

/**
 * Standard WebSocket close codes.
 * https://www.rfc-editor.org/rfc/rfc6455.html#section-7.4.1
 */
object WebSocketCloseCode {
	const val Normal = WebSocket.NORMAL_CLOSURE
	const val GoingAway = 1001
}
