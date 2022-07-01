package com.viral32111.discordrelay.discord.types;

// https://discord.com/developers/docs/topics/opcodes-and-status-codes#gateway-gateway-opcodes
// NOTE: These only include the ones that are used by this mod
public class OperationCode {
	public static final int Dispatch = 0;
	public static final int Heartbeat = 1;
	public static final int Identify = 2;
	public static final int Resume = 6;
	public static final int Reconnect = 7;
	public static final int InvalidSession = 9;
	public static final int Hello = 10;
	public static final int HeartbeatAcknowledgement = 11;
}
