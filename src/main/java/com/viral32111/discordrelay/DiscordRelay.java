// The package this is for
package com.viral32111.discordrelay;

// Import dependencies
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

	// Creates a JSON string to use as the webhook payload
	private String createWebhookPayload( String content, Player player ) {

		// Create a dictionary from the provided content, with all @mentions disabled
		HashMap<String, Object> payload = new HashMap<>() {{
			put( "content", content );
			put( "allowed_mentions", new HashMap<>() {{
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
				System.out.println( "New connection from " + clientSocket.getRemoteSocketAddress() );

				System.out.println( "Sending data to " + clientSocket.getRemoteSocketAddress() );
				OutputStream outputStream = clientSocket.getOutputStream();
				outputStream.write( "HELLO_WORLD\n".getBytes( StandardCharsets.UTF_8 ) );
				outputStream.flush();

				System.out.println( "Goodbye " + clientSocket.getRemoteSocketAddress() );
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
		getServer().getScheduler().runTaskLater( this, () -> executeWebhook( chatWebhook, createWebhookPayload( ":desktop: The server is now online!", null ) ), 1 );

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
		String playerNickname = PlainTextComponentSerializer.plainText().serialize( event.getPlayer().displayName() );

		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( String.format( ":wave_tone1: %s joined!", playerNickname ), null ) ));
	}

	// Runs when a player leaves the server...
	@EventHandler public void onPlayerQuit( PlayerQuitEvent event ) {
		String playerNickname = PlainTextComponentSerializer.plainText().serialize( event.getPlayer().displayName() );

		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( String.format( ":wave_tone1: %s left.", playerNickname ), null ) ));
	}

	// Runs when a player sends a message in chat...
	@EventHandler public void onAsyncChat( AsyncChatEvent event ) {
		String message = PlainTextComponentSerializer.plainText().serialize( event.message() );

		getServer().getScheduler().runTaskAsynchronously( this, () -> executeWebhook( chatWebhook, createWebhookPayload( message, event.getPlayer() ) ));
	}

}
