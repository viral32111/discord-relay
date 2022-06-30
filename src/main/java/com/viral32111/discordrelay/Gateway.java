package com.viral32111.discordrelay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class Gateway implements WebSocket.Listener {


	private WebSocket myself;
	private final List<CharSequence> receivedTextSequences = new ArrayList<>();
	private int heartbeatInterval = 40000; // It's usually around about this
	private int latestSequenceNumber = 0;
	private CompletableFuture<Void> heartbeater;

	private boolean isConnected() {
		return !( myself.isInputClosed() || myself.isInputClosed() );
	}

	private void startHeartbeating() {
		DiscordRelay.logger.info( "Started heartbeating..." );

		boolean isFirst = true;

		while ( isConnected() && !heartbeater.isCancelled() ) {
			if ( isFirst ) {
				isFirst = false;

				double jitter = Math.random();

				try {
					Thread.sleep( Math.round( heartbeatInterval * jitter ) );
				} catch ( InterruptedException e ) {
					e.printStackTrace();
				}
			} else {
				try {
					Thread.sleep( Math.round( heartbeatInterval ) );
				} catch ( InterruptedException e ) {
					e.printStackTrace();
				}
			}

			if ( !isConnected() || heartbeater.isCancelled() ) break;

			DiscordRelay.logger.info( "Sending heartbeat with sequence number {}...", latestSequenceNumber );
			JsonObject payload = new JsonObject();
			payload.addProperty( "op", 1 );
			payload.addProperty( "d", latestSequenceNumber );
			myself.sendText( payload.toString(), true );
		}

		DiscordRelay.logger.info( "Finished heartbeating." );
	}

	@Override
	public void onOpen( WebSocket webSocket ) {
		DiscordRelay.logger.info( "onOpen()" );

		myself = webSocket;

		WebSocket.Listener.super.onOpen( webSocket );
	}

	@Override
	public CompletionStage<?> onText( WebSocket webSocket, CharSequence messageFragment, boolean isLastMessage ) {
		//DiscordRelay.logger.info( "onText() -> '{}' | {}", messageFragment, isLastMessage );

		receivedTextSequences.add( messageFragment );

		if ( isLastMessage ) {
			String message = String.join( "", receivedTextSequences );
			receivedTextSequences.clear();

			JsonObject payload = JsonParser.parseString( message ).getAsJsonObject();
			//DiscordRelay.logger.info( payload.toString() );

			if ( !payload.get( "op" ).isJsonNull() ) {
				int operationCode = payload.get( "op" ).getAsInt();
				//var sequenceNumber = payload.get( "s" ); // Should be an integer, but can be null
				//String eventType = payload.get( "t" ).getAsString(); // Could be null
				//var eventData = payload.get( "d" ); // Possibly don't know the type, could even be null

				if ( !payload.get( "s" ).isJsonNull() ) {
					latestSequenceNumber =  payload.get( "s" ).getAsInt();
					//DiscordRelay.logger.info( "Sequence number updated to {}", latestSequenceNumber );
				} /*else {
					DiscordRelay.logger.warn( "sequence number is null?" );
				}*/

				if ( operationCode == 10 ) {
					DiscordRelay.logger.info( "Received hello!" );

					if ( !payload.get( "d" ).isJsonNull() ) {
						heartbeatInterval = payload.get( "d" ).getAsJsonObject().get( "heartbeat_interval" ).getAsInt();
						DiscordRelay.logger.info( "Heartbeat interval is {}", heartbeatInterval );

						heartbeater = CompletableFuture.runAsync( this::startHeartbeating );

						DiscordRelay.logger.info( "We should identify now..." );

						JsonObject identifyDataProperties = new JsonObject();
						identifyDataProperties.addProperty( "$os", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );
						identifyDataProperties.addProperty( "$browser", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );
						identifyDataProperties.addProperty( "$device", Objects.requireNonNull( Config.Get( "http.user-agent" ) ) );

						JsonObject identifyDataPresence = new JsonObject();
						identifyDataPresence.addProperty( "status", "offline" );

						JsonObject identifyData = new JsonObject();
						identifyData.addProperty( "token", Objects.requireNonNull( Config.Get( "discord.token" ) ) );
						identifyData.addProperty( "intents", ( 1 << 9 ) ); // Server Messages
						identifyData.add( "properties", identifyDataProperties );

						JsonObject identifyPayload = new JsonObject();
						identifyPayload.addProperty( "op", 2 );
						identifyPayload.add( "d", identifyData );

						myself.sendText( identifyPayload.toString(), true );
					} else {
						DiscordRelay.logger.warn( "The event data is null?" );
					}
				} else if ( operationCode == 11 ) {
					DiscordRelay.logger.info( "Received heartbeat acknowledgement!" );
				} else if ( operationCode == 1 ) {
					DiscordRelay.logger.info( "Gateway wants to know if we're still here :o" );

					JsonObject heartbeatPayload = new JsonObject();
					heartbeatPayload.addProperty( "op", 1 );
					heartbeatPayload.addProperty( "d", latestSequenceNumber );
					myself.sendText(heartbeatPayload.toString(), true);
				} else if ( operationCode == 0 ) {
					if ( !payload.get( "t" ).isJsonNull() || !payload.get( "d" ).isJsonNull() ) {
						String type = payload.get( "t" ).getAsString();
						JsonObject data = payload.get( "d" ).getAsJsonObject();

						//DiscordRelay.logger.info( "Received event dispatch for: {}", type );

						if ( type.equals( "READY" ) ) {
							DiscordRelay.logger.info( "Connected as '{}#{}' ({})", data.get( "user" ).getAsJsonObject().get( "username" ).getAsString(), data.get( "user" ).getAsJsonObject().get( "discriminator" ).getAsInt(), data.get( "user" ).getAsJsonObject().get( "id" ).getAsString() );

						} else if ( type.equals( "MESSAGE_CREATE" ) ) {
							String channelID = data.get( "channel_id" ).getAsString();

							if ( channelID.equals( Objects.requireNonNull( Config.Get( "discord.channel.relay" ) ) ) && data.get( "webhook_id" ) == null ) {
								JsonObject author = data.get( "author" ).getAsJsonObject();
								String authorName = author.get( "username" ).getAsString();

								JsonObject member = data.get( "member" ).getAsJsonObject();

								if ( member.has( "nick" ) && !member.get( "nick" ).isJsonNull() ) authorName = member.get( "nick" ).getAsString();

								String content = data.get( "content" ).getAsString();
								if ( !content.equals( "" ) ) {
									//DiscordRelay.logger.info( "Received relayed Discord message '{}' from '{}'!", content, authorName );

									DiscordRelay.broadcastDiscordMessage( authorName, content );
								}

								//DiscordRelay.executeServerCommand( String.format( "tellraw @a [{\"text\":\"(Discord) \",\"color\":\"blue\"},{\"text\":\"%s\",\"color\":\"green\"},{\"text\":\": %s\",\"color\":\"white\"}]", username, content ) );
							} else {
								DiscordRelay.logger.warn( "Ignoring message for channel {}.", channelID );
							}
						} else {
							DiscordRelay.logger.warn( "Ignoring event dispatch for {}.", type );
						}
					} else {
						DiscordRelay.logger.warn( "The event type and/or data is null?" );
					}
				} else {
					DiscordRelay.logger.warn( "Ignoring operation code {}.", operationCode );
				}
			} else {
				DiscordRelay.logger.warn( "The operation code is null?" );
			}
		}

		return WebSocket.Listener.super.onText( webSocket, messageFragment, isLastMessage );
	}

	@Override
	public CompletionStage<?> onBinary( WebSocket webSocket, ByteBuffer data, boolean last ) {
		DiscordRelay.logger.info( "onBinary() -> '{}' | {}", data.toString(), last );

		return WebSocket.Listener.super.onBinary( webSocket, data, last );
	}

	@Override
	public CompletionStage<?> onPing( WebSocket webSocket, ByteBuffer message ) {
		DiscordRelay.logger.info( "onPing() -> '{}'", message.toString() );

		return WebSocket.Listener.super.onPing( webSocket, message );
	}

	@Override
	public CompletionStage<?> onPong( WebSocket webSocket, ByteBuffer message ) {
		DiscordRelay.logger.info( "onPong() -> '{}'", message.toString() );

		return WebSocket.Listener.super.onPong( webSocket, message );
	}

	@Override
	public CompletionStage<?> onClose( WebSocket webSocket, int statusCode, String reason ) {
		DiscordRelay.logger.info( "onClose() -> '{}' | {}", reason, statusCode );

		heartbeater.cancel( true );

		return WebSocket.Listener.super.onClose( webSocket, statusCode, reason );
	}

	@Override
	public void onError( WebSocket webSocket, Throwable error ) {
		DiscordRelay.logger.error( "onError() -> {} {} {}", error.toString(), error.getMessage(), error.getStackTrace() );

		WebSocket.Listener.super.onError( webSocket, error );
	}
}
