// The package this is for
package com.viral32111.discordrelay;

// Import dependencies
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.simple.parser.JSONParser;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

// Create the main class
@SuppressWarnings( { "unused" } )
public class DiscordRelay extends JavaPlugin implements Listener {

	// Define global variables
	private URI chatWebhook;
	private String userAgentHeader, fromHeader;
	private HttpClient webhookClient;
	private AFUNIXServerSocket relayServer;
	private File relayFile;
	private BukkitTask relayListenTask;
	private long doneLoadingUnixTimestamp;

	// Creates a JSON string to use as the webhook payload
	private String createWebhookPayload( String content, Player player ) {

		// Create a dictionary from the provided content, with all @mentions disabled
		HashMap<String, Object> payload = new HashMap<>() {{
			put( "content", content );
			put( "allowed_mentions", new HashMap<String, Object>() {{
				put( "parse", new JSONArray() );
			}} );
		}};

		// Add the player's nickname and avatar to the payload, if applicable
		if ( player != null ) {
			payload.put( "username", PlainTextComponentSerializer.plainText().serialize( player.displayName() ) );
			payload.put( "avatar_url", String.format( "https://crafthead.net/helm/%s", player.getUniqueId() ) );
		}

		// Convert the dictionary to a JSON string then return it
		return new JSONObject( payload ).toJSONString();

	}

	// Executes a Discord webhook with the provided payload
	private void executeWebhook( URI uri, String payload ) {

		// Create a new HTTP request to POST the webhook JSON payload
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
		requestBuilder.uri( uri );
		requestBuilder.method( "POST", HttpRequest.BodyPublishers.ofString( payload ) );
		requestBuilder.header( "Content-Type", "application/json" );
		if ( userAgentHeader != null ) requestBuilder.header( "User-Agent", userAgentHeader );
		if ( fromHeader != null ) requestBuilder.header( "From", fromHeader );
		HttpRequest request = requestBuilder.build();

		// Safely execute that HTTP request
		try {
			webhookClient.send( request, HttpResponse.BodyHandlers.ofString() );
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}

	}

	// Listens for incoming connections to the unix domain socket...
	private void relayListen() {

		// Loop forever if the relay server is valid
		while ( relayServer != null ) {

			// Stop looping if the relay server is closed
			if ( relayServer.isClosed() ) break;

			// Safely accept new connections, send them example data, then disconnect them
			try {
				AFUNIXSocket clientSocket = relayServer.accept();
				InputStream inputStream = clientSocket.getInputStream();
				OutputStream outputStream = clientSocket.getOutputStream();

				byte[] inputBuffer = new byte[ 1024 ];
				int bytesRead = inputStream.read( inputBuffer );
				JSONObject payload = ( JSONObject ) new JSONParser().parse( new String( inputBuffer, 0, bytesRead, StandardCharsets.UTF_8 ) );

				/* Types:
					0: Fetch Status
					1: Chat Message (has data.username, data.color & data.content)
					2: Remote Command
				*/

				/* Statuses:
					0: Success (data may be empty)
					1: Error (see data.reason)
				*/

				long type = ( long ) payload.get( "type" );
				JSONObject data = ( JSONObject ) payload.get( "data" );

				HashMap<String, Object> response = new HashMap<>();

				if ( type == 0 ) {
					// needs cpu & memory usage

					HashSet<JSONObject> players = new HashSet<>();
					for ( Player player : getServer().getOnlinePlayers() ) {
						players.add( new JSONObject( new HashMap<String, String>() {{
							put( "username", player.getName() );
							put( "nickname", PlainTextComponentSerializer.plainText().serialize( player.displayName() ) );
							put( "uuid", player.getUniqueId().toString() );
						}} ) );
					}

					response.put( "status", 0 );
					response.put( "data", new HashMap<>() {{
						put( "loaded_at", doneLoadingUnixTimestamp );
						put( "tps", new HashMap<String, Double>() {{
							put( "1m", getServer().getTPS()[ 0 ] );
							put( "5m", getServer().getTPS()[ 1 ] );
							put( "15m", getServer().getTPS()[ 2 ] );
						}} );
						put( "version", new HashMap<String, String>() {{
							put( "name", getServer().getName() );
							put( "native", getServer().getVersion() );
							put( "bukkit", getServer().getBukkitVersion() );
							put( "minecraft", getServer().getMinecraftVersion() );
						}} );
						put( "players", players );
					}} );

				} else if ( type == 1 ) {
					String username = ( String ) data.get( "username" );
					long color = ( long ) data.get( "color" );
					String content = ( String ) data.get( "content" );

					TextComponent.Builder messageComponentBuilder = Component.text();
					messageComponentBuilder.append( Component.text( "(Discord) ", TextColor.color( 0x5865F2 ) ) );
					messageComponentBuilder.append( Component.text( username, TextColor.color( Math.round( color ) ) ) );
					messageComponentBuilder.append( Component.text( ": " + content, NamedTextColor.WHITE ) );

					getServer().broadcast( messageComponentBuilder.build() );

					response.put( "status", 0 );
					response.put( "data", new HashMap<String, Object>() );

				} else if ( type == 2 ) {
					response.put( "status", 1 );
					response.put( "data", new HashMap<String, Object>() {{
						put( "reason", "Not implemented yet." );
					}} );

				} else {
					response.put( "status", 1 );
					response.put( "data", new HashMap<String, Object>() {{
						put( "reason", "Unknown type." );
					}} );
				}

				outputStream.write( new JSONObject( response ).toJSONString().getBytes( StandardCharsets.UTF_8 ) );
				outputStream.flush();

				clientSocket.close();
			} catch ( Exception exception ) {
				exception.printStackTrace();
			}

		}

	}

	// Runs when the plugin is loaded...
	@Override public void onEnable() {

		// Create the default configuration file
		saveDefaultConfig();

		// Load the #chat webhook URL from the configuration file
		String chatWebhookValue = getConfig().getString( "webhooks.chat" );
		if ( chatWebhookValue != null ) chatWebhook = URI.create( chatWebhookValue );

		// Load the #logs webhook URL from the configuration file
		//String logsWebhookValue = getConfig().getString( "webhooks.logs" );
		//if ( logsWebhookValue != null ) logsWebhook = URI.create( logsWebhookValue );

		// Load the #console webhook URL from the configuration file
		//String consoleWebhookValue = getConfig().getString( "webhooks.console" );
		//if ( consoleWebhookValue != null ) consoleWebhook = URI.create( consoleWebhookValue );

		// Load the HTTP headers from the configuration file
		userAgentHeader = getConfig().getString( "http.user-agent" );
		fromHeader = getConfig().getString( "http.from" );

		// Load the unix socket path from the configuration file
		String relayPath = getConfig().getString( "socket.path" );

		// Register the event listeners
		getServer().getPluginManager().registerEvents( this, this );

		// Create a new HTTP client for executing webhooks
		webhookClient = HttpClient.newHttpClient();

		// Safely ereate the unix domain socket and start listening for incoming connections
		if ( relayPath != null ) {
			try {
				relayFile = new File( relayPath );
				relayServer = AFUNIXServerSocket.newInstance();
				relayServer.bind( new AFUNIXSocketAddress( relayFile ) );
				relayListenTask = getServer().getScheduler().runTaskAsynchronously( this, this::relayListen );
			} catch ( Exception exception ) {
				exception.printStackTrace();
			}
		}

		// Send a now online message when the server finishes loading
		getServer().getScheduler().runTaskLater( this, () -> {
			doneLoadingUnixTimestamp = System.currentTimeMillis();
			executeWebhook( chatWebhook, createWebhookPayload( ":desktop: The server is now online!", null ) );
		}, 1 );

	}

	// Runs when the plugin is unloaded...
	@Override public void onDisable() {

		// Close unix domain socket
		try {
			relayListenTask.cancel();
			relayServer.close();
			relayFile.delete();
		} catch ( Exception exception ) {
			exception.printStackTrace();
		}

		// Send a now offline message
		executeWebhook( chatWebhook, createWebhookPayload( ":coffin: The server has gone offline.", null ) );

	}

	// Runs when a player joins the server...
	@EventHandler public void onPlayerJoin( PlayerJoinEvent event ) {

		// Get the plaintext representation of the player's nickname
		String nickname = PlainTextComponentSerializer.plainText().serialize( event.getPlayer().displayName() );

		// Send a player joined message
		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( String.format( ":wave_tone1: %s joined!", nickname ), null ) ));

	}

	// Runs when a player leaves the server...
	@EventHandler public void onPlayerQuit( PlayerQuitEvent event ) {

		// Get the plaintext representation of the player's nickname
		String nickname = PlainTextComponentSerializer.plainText().serialize( event.getPlayer().displayName() );

		// Send a player left message
		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( String.format( ":wave_tone1: %s left.", nickname ), null ) ));

	}

	// Runs when a player sends a message in chat...
	@EventHandler public void onAsyncChat( AsyncChatEvent event ) {

		// Get the plaintext representation of the player's message
		String message = PlainTextComponentSerializer.plainText().serialize( event.message() );

		// Send a chat message
		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( message, event.getPlayer() ) ));

	}

}
