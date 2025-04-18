# TeleportAssist

TeleportAssist is a Minecraft plugin designed for Minecraft servers that allows players to send and accept teleport requests. It provides commands for teleporting to other players, accepting or denying teleport requests, and teleporting back to a previous location.

## Features

- **Teleport Requests**: Players can request to teleport to each other using the `/tpa` command.
- **Accept/Deny Requests**: Players can accept or deny teleport requests with `/tpaccept` and `/tpdeny`.
- **Teleport Here**: Players can request others to teleport to them using the `/tpahere` command.
- **Back Command**: Players can return to their previous location using the `/back` command.
- **Asynchronous Teleportation**: Teleportation is handled asynchronously to prevent server lag.

## Commands

- `/tpa <player>`: Request to teleport to another player.
- `/tpaccept <player>`: Accept a teleport request from another player.
- `/tpdeny`: Deny a teleport request.
- `/tpahere <player>`: Request another player to teleport to you.
- `/back`: Teleport back to your previous location.

## Configuration

The plugin does not require any configuration files. It automatically detects if the server is running on Folia or Paper and adjusts its behavior accordingly.

## Acknowledgments

- Thanks to the Bukkit community for their support and resources.
- Special thanks to the developers of Folia and Paper for their contributions to the Minecraft server ecosystem.

## Contact

For issues, feature requests, or contributions, please open an issue on the project's repository or contact the developer directly.
