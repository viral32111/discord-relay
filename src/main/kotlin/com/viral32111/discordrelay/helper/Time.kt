package com.viral32111.discordrelay.helper

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val utcZone = ZoneId.of( "UTC" )
private const val defaultFormat = "dd/MM/yyyy HH:mm:ss 'UTC'"

fun getCurrentDateTimeUTC( format: String = defaultFormat ): String = ZonedDateTime.now( utcZone ).format( DateTimeFormatter.ofPattern( format ) )
fun getCurrentDateTimeISO8601(): String = ZonedDateTime.now( utcZone ).format( DateTimeFormatter.ISO_OFFSET_DATE_TIME )

fun Instant.formatInUTC( format: String = defaultFormat ): String = DateTimeFormatter.ofPattern( format ).format( this.atZone( utcZone ) )

fun Long.toHumanReadableTime(): String = listOf(
		this / ( 24 * 3600 ) to "day",
		this / 3600 % 24 to "hour",
		this / 60 % 60 to "minute",
		this % 60 to "second"
	)
	.filter { it.first > 0 }
	.joinToString( ", " ) { "${ it.first } ${ it.second }${ if ( it.first > 1 ) "s" else "" }" }
	.ifEmpty { "less than a second" }
