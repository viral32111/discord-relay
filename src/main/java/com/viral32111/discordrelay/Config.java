package com.viral32111.discordrelay;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Manages creating, loading and retrieving values from the configuration file
public class Config {

	// The name of the configuration file inside the server's config directory
	private static final String CONFIGURATION_FILE_NAME = "DiscordRelay.json";

	// Create a Gson instance with pretty-printing for writing the example configuration file
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	// Holds all key-value pairs from the configuration file
	// NOTE: Dots (.) are used in the key for accessing nested keys
	private static final HashMap<String, String> CONFIGURATION_VALUES = new HashMap<>();

	// Reads the values from configuration file, or creates the example one if one does not exist
	public static void Load() throws IOException {

		// The server config directory and our configuration file
		File serverConfigDirectory = FabricLoader.getInstance().getConfigDir().toFile();
		File configurationFile = new File( serverConfigDirectory, CONFIGURATION_FILE_NAME );

		// Create the file with the example keys & values if it does not exist
		if ( !configurationFile.exists() ) try ( FileWriter writer = new FileWriter( configurationFile ) ) {
			writer.write( GSON.toJson( PopulateWithExample( null ) ) );
		}

		// Read the file as JSON and update the property for later use
		try ( FileReader reader = new FileReader( configurationFile ) ) {
			JsonObject existingConfiguration = JsonParser.parseReader( reader ).getAsJsonObject();

			// Ensure that the configuration has all the expected keys
			existingConfiguration = PopulateWithExample( existingConfiguration );

			// Recursively read all key-value pairs from the configuration
			StoreProperties( existingConfiguration, "" );

			// Save the configuration in case new keys were added
			try ( FileWriter writer = new FileWriter( configurationFile ) ) {
				writer.write( GSON.toJson( existingConfiguration ) );
			}

		}

	}

	// Recursively loops through every key-value pair in a JSON object and adds it to the configuration
	private static void StoreProperties( JsonObject rootConfiguration, String parentKey ) {

		// Loop through all key-value pairs for this node
		for ( Map.Entry<String, JsonElement> entry : rootConfiguration.entrySet() ) {

			// If the value is a string, add it to the configuration
			if ( entry.getValue().isJsonPrimitive() ) {
				//DiscordRelay.LOGGER.info( "Configuration key '{}' is '{}'", parentKey.concat( entry.getKey() ), entry.getValue().getAsString() );
				CONFIGURATION_VALUES.put( parentKey.concat( entry.getKey() ), entry.getValue().getAsString() );

			// Otherwise, recall with the value as the next node, and the path to get to it
			} else {
				StoreProperties( entry.getValue().getAsJsonObject(), String.format( "%s%s.", parentKey, entry.getKey() ) );
			}

		}

	}

	// Gets a string value from the configuration, and applies optional placeholder replacement
	// NOTE: Dots (.) can be used in the path for accessing nested keys
	public static String Get( String path, @Nullable Map<String, String> placeholderValues ) {

		// Get the value from the configuration, requiring it to exist
		String value = Objects.requireNonNull( CONFIGURATION_VALUES.get( path ) );

		// Apply any placeholder replacements to the value before returning if any were given
		if ( placeholderValues != null ) for ( Map.Entry<String, String> pair : placeholderValues.entrySet() ) {
			value = value.replaceAll( String.format( "\\{%s\\}", pair.getKey() ), pair.getValue() );
		}

		// Return the configuration value
		return value;

	}

	// Gets an integer value from the configuration
	public static Integer Get( String path ) {
		return Integer.parseInt( Get( path, null ) );
	}

	// Creates a new example configuration, or adds missing keys to an existing configuration
	// NOTE: An alternative way to do this would be with a version identifier in the configuration file
	// TODO: Use the config.json resource instead
	private static JsonObject PopulateWithExample( @Nullable JsonObject rootConfiguration ) {

		// If no configuration was passed, then start with an empty one
		if ( rootConfiguration == null ) rootConfiguration = new JsonObject();

		// Log embed date format & public server address are not part of any parent configuration, so they go in the root
		if ( !rootConfiguration.has( "log-date-format" ) ) rootConfiguration.addProperty( "log-date-format", "dd/MM/yyyy HH:mm:ss z" );
		if ( !rootConfiguration.has( "public-server-address" ) ) rootConfiguration.addProperty( "public-server-address", "127.0.0.1:25565" );

		// Get the existing Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordConfiguration = ( rootConfiguration.has( "discord" ) ? rootConfiguration.getAsJsonObject( "discord" ) : new JsonObject() );
		if ( !discordConfiguration.has( "token" ) ) discordConfiguration.addProperty( "token", "APPLICATION-TOKEN" );
		if ( !discordConfiguration.has( "library" ) ) discordConfiguration.addProperty( "library", "Minecraft Server" );

		// Get the existing API configuration from the Discord configuration, or create an empty one, then add any missing properties
		// https://discord.com/developers/docs/reference#api-reference-base-url
		JsonObject discordApiConfiguration = ( discordConfiguration.has( "api" ) ? discordConfiguration.getAsJsonObject( "api" ) : new JsonObject() );
		if ( !discordApiConfiguration.has( "url" ) ) discordApiConfiguration.addProperty( "url", "discord.com/api" );
		if ( !discordApiConfiguration.has( "version" ) ) discordApiConfiguration.addProperty( "version", "10" );

		// Get the existing channel configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordChannelConfiguration = ( discordConfiguration.has( "channel" ) ? discordConfiguration.getAsJsonObject( "channel" ) : new JsonObject() );
		if ( !discordChannelConfiguration.has( "relay" ) ) discordChannelConfiguration.addProperty( "relay", "12345678987654321" );

		// Get the existing category configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordCategoryConfiguration = ( discordConfiguration.has( "category" ) ? discordConfiguration.getAsJsonObject( "category" ) : new JsonObject() );
		if ( !discordCategoryConfiguration.has( "id" ) ) discordCategoryConfiguration.addProperty( "id", "12345678987654321" );
		if ( !discordCategoryConfiguration.has( "name" ) ) discordCategoryConfiguration.addProperty( "name", "Minecraft ({status})" );

		// Get the existing wehook configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordWebhookConfiguration = ( discordConfiguration.has( "webhook" ) ? discordConfiguration.getAsJsonObject( "webhook" ) : new JsonObject() );
		if ( !discordWebhookConfiguration.has( "relay" ) ) discordWebhookConfiguration.addProperty( "relay", "ID/TOKEN" );
		if ( !discordWebhookConfiguration.has( "log" ) ) discordWebhookConfiguration.addProperty( "log", "ID/TOKEN" );

		// Always add all of the child configurations to the root because properties inside of sub-child configurations may have changed
		discordConfiguration.add( "api", discordApiConfiguration );
		discordConfiguration.add( "channel", discordChannelConfiguration );
		discordConfiguration.add( "category", discordCategoryConfiguration );
		discordConfiguration.add( "webhook", discordWebhookConfiguration );

		// Get the existing HTTP configuration, or create an empty one, then add any missing properties
		JsonObject httpConfiguration = ( rootConfiguration.has( "http" ) ? rootConfiguration.getAsJsonObject( "http" ) : new JsonObject() );
		if ( !httpConfiguration.has( "user-agent" ) ) httpConfiguration.addProperty( "user-agent", "Minecraft Server (https://example.com; contact@example.com)" );
		if ( !httpConfiguration.has( "from" ) ) httpConfiguration.addProperty( "from", "contact@example.com" );
		if ( !httpConfiguration.has( "timeout" ) ) httpConfiguration.addProperty( "timeout", "10" ); // Seconds

		// Get the existing external/third-party configuration, or create an empty one, then add any missing properties
		JsonObject externalConfiguration = ( rootConfiguration.has( "external" ) ? rootConfiguration.getAsJsonObject( "external" ) : new JsonObject() );
		if ( !externalConfiguration.has( "profile" ) ) externalConfiguration.addProperty( "profile", "https://namemc.com/profile/{uuid}" );
		if ( !externalConfiguration.has( "face" ) ) externalConfiguration.addProperty( "face", "https://crafatar.com/avatars/{uuid}.png?size=128&overlay" );

		// Always add all of the child configurations to the root because properties inside of sub-child configurations may have changed
		rootConfiguration.add( "discord", discordConfiguration );
		rootConfiguration.add( "http", httpConfiguration );
		rootConfiguration.add( "external", externalConfiguration );

		// Give back the modified or new configuration
		return rootConfiguration;

	}

}
