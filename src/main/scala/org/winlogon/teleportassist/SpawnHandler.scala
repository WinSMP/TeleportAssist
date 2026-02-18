// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.Location
import org.bukkit.entity.Player

import Messages.sendBuiltinMessage

class SpawnHandler(
    plugin: TeleportAssist,
    database: Database,
    teleportService: TeleportService,
    scheduler: Scheduler
) {
    /** Sets the world spawn to the player's current location. */
    def setSpawn(player: Player): Unit = {
        database.setSpawn(player.getLocation)
        player.sendBuiltinMessage(Messages.Notice.SpawnSet, "name" -> player.getWorld.getName)
    }

    /** Teleports the player to the world's configured spawn location with warmup. */
    def teleportToSpawn(player: Player): Unit = {
        database.getSpawn(player.getWorld.getName) match {
            case Some(spawn) =>
                val dest = Location(
                    player.getWorld, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch
                )
                teleportService.teleportWithWarmup(
                    player, dest,
                    Messages.format(Messages.Notice.SpawnTeleported),
                    scheduler = scheduler
                )
            case None =>
                player.sendBuiltinMessage(Messages.Notice.NoSpawnSet)
        }
    }
}
