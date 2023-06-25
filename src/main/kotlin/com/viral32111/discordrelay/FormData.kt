package com.viral32111.discordrelay

import java.io.ByteArrayOutputStream

data class FormData(
	val boundary: String,
	val textSections: List<FormDataSectionText>,
	val bytesSections: List<FormDataSectionBytes>
) {
	fun toByteArray(): ByteArray {
		val crlf = "\r\n"
		val structure = ByteArrayOutputStream()
		structure.write( "--$boundary$crlf" )

		textSections.forEachIndexed { index, it ->
			val parameters = it.parameters.map { parameter -> "${ parameter.key }=\"${ parameter.value }\"" }.joinToString( "; " )

			structure.write( "Content-Type: ${ it.contentType }$crlf" )
			structure.write( "Content-Disposition: form-data; name=\"${ it.name }\"${ if ( parameters.isNotBlank() ) "; $parameters" else "" }$crlf$crlf" )
			structure.write( it.value )
			structure.write( if ( index != ( textSections.size - 1 ) || bytesSections.isNotEmpty() ) "$crlf--$boundary$crlf" else "$crlf--$boundary--$crlf" )
		}

		bytesSections.forEachIndexed { index, it ->
			val parameters = it.parameters.map { parameter -> "${ parameter.key }=\"${ parameter.value }\"" }.joinToString( "; " )

			structure.write( "Content-Type: ${ it.contentType }$crlf" )
			structure.write( "Content-Disposition: form-data; name=\"${ it.name }\"${ if ( parameters.isNotBlank() ) "; $parameters" else "" }$crlf$crlf" )
			structure.writeBytes( it.value.toByteArray() )
			structure.write( if ( index != ( bytesSections.size - 1 ) ) "$crlf--$boundary$crlf" else "$crlf--$boundary--$crlf" )
		}

		return structure.toByteArray()
	}

	override fun toString(): String = toByteArray().toString( Charsets.UTF_8 )
}

private fun ByteArrayOutputStream.write( str: String ) = writeBytes( str.toByteArray() )

class FormDataBuilder {
	private var boundary = System.nanoTime().toString()
	private val textSections = mutableListOf<FormDataSectionText>()
	private val bytesSections = mutableListOf<FormDataSectionBytes>()

	fun addTextSection( block: FormDataSectionTextBuilder.() -> Unit ) = textSections.add( FormDataSectionTextBuilder().apply( block ).build() )
	fun addBytesSection( block: FormDataSectionBytesBuilder.() -> Unit ) = bytesSections.add( FormDataSectionBytesBuilder().apply( block ).build() )

	fun build() = FormData( boundary, textSections, bytesSections )
}

fun createFormData( block: FormDataBuilder.() -> Unit ) = FormDataBuilder().apply( block ).build()

data class FormDataSectionText(
	val name: String,
	val value: String,
	val contentType: String,
	val parameters: Map<String, String>
)

class FormDataSectionTextBuilder {
	var name: String = ""
	var value: String = ""
	var contentType: String = "text/plain"
	private var parameters: MutableMap<String, String> = mutableMapOf()

	fun build() = FormDataSectionText( name, value, contentType, parameters )
}

data class FormDataSectionBytes(
	val name: String,
	val value: List<Byte>,
	val contentType: String,
	val parameters: Map<String, String>
)

class FormDataSectionBytesBuilder {
	var name: String = ""
	var value: MutableList<Byte> = mutableListOf()
	var contentType: String = "application/octet-stream"
	var parameters: MutableMap<String, String> = mutableMapOf()

	fun build() = FormDataSectionBytes( name, value, contentType, parameters )
}
