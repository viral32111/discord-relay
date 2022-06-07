package com.viral32111.discordrelay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

public class Config {
	public static URI relayWebhook = URI.create( "https://discord.com/api/webhooks/ID/TOKEN" );
	public static URI logsWebhook = URI.create( "https://discord.com/api/webhooks/ID/TOKEN" );
	public static String logsDateFormat = "dd/MM/yyyy HH:mm:ss z";
	public static String categoryID = "12345678987654321";
	public static String categoryFormat = "Minecraft (%s)";
	public static String httpUserAgent = "Minecraft Server (https://example.com; contact@example.com)";
	public static String httpFrom = "contact@example.com";
	public static String botToken = "APPLICATION-TOKEN";
	public static String skinAvatarURL = "https://crafatar.com/avatars/%s.png?size=128&overlay";
	public static String profileURL = "https://namemc.com/profile/%s";
	public static String serverAddress = "127.0.0.1:25565";
	public static String relayChannelID = "12345678987654321";

	private static final Gson jsonHandler = new GsonBuilder().setPrettyPrinting().create();

	public static void load() throws IOException {
		File file = new File( FabricLoader.getInstance().getConfigDir().toFile(), "discordrelay.json" );

		if ( file.exists() ) {
			FileReader reader = new FileReader( file );
			JsonObject config = JsonParser.parseReader( reader ).getAsJsonObject();
			reader.close();

			// TODO: This fucks up if the config file already exists and a new key has been added in a later version of this mod...

			relayWebhook = URI.create( config.get( "relay-webhook" ).getAsString() );
			logsWebhook = URI.create( config.get( "logs-webhook" ).getAsString() );
			logsDateFormat = config.get( "logs-date-format" ).getAsString();
			categoryID = config.get( "category-id" ).getAsString();
			categoryFormat = config.get( "category-format" ).getAsString();
			httpUserAgent =  config.get( "http-user-agent" ).getAsString();
			httpFrom = config.get( "http-from" ).getAsString();
			botToken = config.get( "bot-token" ).getAsString();
			skinAvatarURL = config.get( "skin-avatar-url" ).getAsString();
			profileURL = config.get( "profile-url" ).getAsString();
			serverAddress = config.get( "server-address" ).getAsString();
			relayChannelID = config.get( "relay-channel-id" ).getAsString();
		} else {
			JsonObject defaultConfig = new JsonObject();
			defaultConfig.addProperty( "relay-webhook", relayWebhook.toString() );
			defaultConfig.addProperty( "logs-webhook", logsWebhook.toString() );
			defaultConfig.addProperty( "logs-date-format", logsDateFormat );
			defaultConfig.addProperty( "category-id", categoryID );
			defaultConfig.addProperty( "category-format", categoryFormat );
			defaultConfig.addProperty( "http-user-agent", httpUserAgent );
			defaultConfig.addProperty( "http-from", httpFrom );
			defaultConfig.addProperty( "bot-token", botToken );
			defaultConfig.addProperty( "skin-avatar-url", skinAvatarURL );
			defaultConfig.addProperty( "profile-url", profileURL );
			defaultConfig.addProperty( "server-address", serverAddress );
			defaultConfig.addProperty( "relay-channel-id", relayChannelID );

			FileWriter writer = new FileWriter( file );
			writer.write( jsonHandler.toJson( defaultConfig ) );
			writer.close();
		}
	}
}
