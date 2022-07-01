# Discord Relay

This is a Minecraft mod for [Fabric](https://fabricmc.net/) that relays in-game chat messages between Minecraft and a Discord channel, allowing for easy communication between players currently on a Minecraft server and members in a Discord server.

This was previously a plugin for [Paper](https://papermc.io/), however development of that concluded but is still available on the [paper branch](https://github.com/viral32111/DiscordRelay/tree/paper).

## Configuration

The configuration file is located at `config/DiscordRelay.json` in your server's directory.

The default is as follows.

```json
{
	"discord": {
		"token": "APPLICATION-TOKEN",
		"api": {
			"url": "discord.com/api",
			"version": "10"
		},
		"channel": {
			"relay": "12345678987654321"
		},
		"category": {
			"id": "12345678987654321",
			"name": "Minecraft (%s)"
		},
		"webhook": {
			"relay": "ID/TOKEN",
			"log": "ID/TOKEN"
		}
	},
	"http": {
		"user-agent": "Minecraft Server (https://example.com; contact@example.com)",
		"from": "contact@example.com",
		"timeout": "10"
	},
	"external": {
		"profile": "https://namemc.com/profile/%s",
		"face": "https://crafatar.com/avatars/%s.png?size=128&overlay"
	},
	"log-date-format": "dd/MM/yyyy HH:mm:ss z",
	"public-server-address": "127.0.0.1:25565"
}
```

## License

Copyright (C) 2021-2022 [viral32111](https://viral32111.com).

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see https://www.gnu.org/licenses.
