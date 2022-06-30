package com.viral32111.discordrelay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

// Manages creating, loading and retrieving values from the configuration file
public class Config {

	// The name of the configuration file inside the server's config directory
	private static final String CONFIGURATION_FILE_NAME = "DiscordRelay.json";

	// Create a Gson instance with pretty-printing for writing the example configuration file
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// Holds the entire JSON object from the configuration file
	// NOTE: A JSON object is used instead of a HashMap so that nested keys will work
	private static JsonObject CONFIGURATION_VALUES = null;
	//private static final HashMap<String, String> CONFIGURATION_VALUES = new HashMap<>();

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

			// Ensure that the configuration has the expected keys
			// NOTE: If it is missing keys, then the example values are used
			CONFIGURATION_VALUES = PopulateWithExample( existingConfiguration );

			// Re-write the configuration in case new keys or values were added in the above call
			try ( FileWriter writer = new FileWriter( configurationFile ) ) {
				writer.write( GSON.toJson( CONFIGURATION_VALUES ) );
			}

		}

	}

	// Gets a value from the configuration
	// NOTE: Property name allows use of a dot to access nested keys
	// TODO: Really this shouldn't be nullable as the keys we request are ones that will always be in the configuration due to calling PopulateWithExample() on the loaded config
	public static @Nullable String Get( String path ) {

		// Split the path up every dot character
		String[] keys = path.split( "\\." );

		// Default to the root configuration
		// NOTE: This changes every time the loop encounters a nested key
		JsonObject parentObject = CONFIGURATION_VALUES;

		// Loop through all keys in the above array
		for ( int index = 0; index < keys.length; index++ ) {
			String key = keys[ index ];

			// If this is the final iteration of the loop then return the value in the parent object with the current key
			if ( ( index + 1 ) == keys.length ) {
				return parentObject.get( key ).getAsString();

			// Otherwise, update the parent object with the current object at the current key
			} else {
				parentObject = parentObject.getAsJsonObject( key );
			}

		}

		// If nothing was found, return null
		return null;

	}

	// Creates a new example configuration, or adds missing keys to an existing configuration
	// NOTE: An alternative way to do this would be with a version identifier in the configuration file
	private static JsonObject PopulateWithExample( @Nullable JsonObject rootConfiguration ) {

		// If no configuration was passed, then start with an empty one
		if ( rootConfiguration == null ) rootConfiguration = new JsonObject();

		// Log embed date format & public server address are not part of any parent configuration, so they go in the root
		if ( !rootConfiguration.has( "log-date-format" ) ) rootConfiguration.addProperty( "log-date-format", "dd/MM/yyyy HH:mm:ss z" );
		if ( !rootConfiguration.has( "public-server-address" ) ) rootConfiguration.addProperty( "public-server-address", "127.0.0.1:25565" );

		// Get the existing Discord configuration, or create an empty one
		JsonObject discordConfiguration = ( rootConfiguration.has( "discord" ) ? rootConfiguration.getAsJsonObject( "discord" ) : new JsonObject() );

		// Get the existing channel configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordChannelConfiguration = ( discordConfiguration.has( "channel" ) ? rootConfiguration.getAsJsonObject( "channel" ) : new JsonObject() );
		if ( !rootConfiguration.has( "relay" ) ) discordChannelConfiguration.addProperty( "relay", "12345678987654321" );

		// Get the existing category configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordCategoryConfiguration = ( discordConfiguration.has( "category" ) ? rootConfiguration.getAsJsonObject( "category" ) : new JsonObject() );
		if ( !discordCategoryConfiguration.has( "id" ) ) discordCategoryConfiguration.addProperty( "id", "12345678987654321" );
		if ( !discordCategoryConfiguration.has( "format" ) ) discordCategoryConfiguration.addProperty( "format", "Minecraft (%s)" );

		// Get the existing wehook configuration from the Discord configuration, or create an empty one, then add any missing properties
		JsonObject discordWebhookConfiguration = ( discordConfiguration.has( "webhook" ) ? rootConfiguration.getAsJsonObject( "webhook" ) : new JsonObject() );
		if ( !discordWebhookConfiguration.has( "relay" ) ) discordWebhookConfiguration.addProperty( "relay", "https://discord.com/api/webhooks/ID/TOKEN" );
		if ( !discordWebhookConfiguration.has( "log" ) ) discordWebhookConfiguration.addProperty( "log", "https://discord.com/api/webhooks/ID/TOKEN" );

		// Get the existing HTTP configuration, or create an empty one, then add any missing propertie
		JsonObject httpConfiguration = ( rootConfiguration.has( "http" ) ? rootConfiguration.getAsJsonObject( "http" ) : new JsonObject() );
		if ( !httpConfiguration.has( "user-agent" ) ) httpConfiguration.addProperty( "user-agent", "Minecraft Server (https://example.com; contact@example.com)" );
		if ( !httpConfiguration.has( "from" ) ) httpConfiguration.addProperty( "from", "contact@example.com" );

		// Get the existing external/third-party configuration, or create an empty one, then add any missing propertie
		JsonObject externalConfiguration = ( rootConfiguration.has( "external" ) ? rootConfiguration.getAsJsonObject( "external" ) : new JsonObject() );
		if ( !externalConfiguration.has( "profile" ) ) externalConfiguration.addProperty( "profile", "https://namemc.com/profile/%s" );
		if ( !externalConfiguration.has( "face" ) ) externalConfiguration.addProperty( "face", "https://crafatar.com/avatars/%s.png?size=128&overlay" );

		// Always add all of the child configurations to the root because properties inside of sub-child configurations may have changed
		rootConfiguration.add( "discord", discordConfiguration );
		rootConfiguration.add( "http", httpConfiguration );
		rootConfiguration.add( "external", externalConfiguration );

		// Give back the modified or new configuration
		return rootConfiguration;

	}

}
