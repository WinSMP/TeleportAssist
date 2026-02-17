// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

import scala.jdk.CollectionConverters.*

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

import Messages.sendBuiltinMessage

class WarpHandler(
    plugin: TeleportAssist,
    database: Database,
    teleportService: TeleportService,
    scheduler: Scheduler
) {
    def createWarp(player: Player, name: String): Unit = {
        if (database.getWarp(name).isDefined) {
            player.sendBuiltinMessage(Messages.Error.WarpAlreadyExists, "name" -> name)
            return
        }
        database.createWarp(name, player.getLocation, Some(player.getUniqueId))
        player.sendBuiltinMessage(Messages.Notice.WarpCreated, "name" -> name)
    }

    def removeWarp(player: Player, name: String): Unit = {
        if (database.removeWarp(name)) {
            player.sendBuiltinMessage(Messages.Notice.WarpRemoved, "name" -> name)
        } else {
            player.sendBuiltinMessage(Messages.Error.WarpNotFound, "name" -> name)
        }
    }

    def editWarp(player: Player, name: String): Unit = {
        if (database.updateWarp(name, player.getLocation)) {
            player.sendBuiltinMessage(Messages.Notice.WarpUpdated, "name" -> name)
        } else {
            player.sendBuiltinMessage(Messages.Error.WarpNotFound, "name" -> name)
        }
    }

    def teleportToWarp(player: Player, name: String): Unit = {
        database.getWarp(name) match {
            case Some(warp) =>
                val world = Bukkit.getWorld(warp.world)
                if (world == null) {
                    player.sendBuiltinMessage(Messages.Error.InvalidWorld)
                    return
                }
                val dest = Location(world, warp.x, warp.y, warp.z, warp.yaw, warp.pitch)
                teleportService.teleportWithWarmup(
                    player, dest,
                    Messages.format(Messages.Notice.WarpTeleported, "name" -> warp.name),
                    scheduler = scheduler
                )
            case None =>
                player.sendBuiltinMessage(Messages.Error.WarpNotFound, "name" -> name)
        }
    }

    def listWarps(player: Player): Unit = {
        val warps = database.listWarps()
        if (warps.isEmpty) {
            player.sendBuiltinMessage(Messages.Notice.NoWarps)
        } else {
            val components = warps.map(name =>
                Component.text(name, NamedTextColor.DARK_AQUA)
            )
            val separator = Component.text(", ", NamedTextColor.GRAY)
            val listMsg = Component.text()
                .append(Component.text("Warps: ", NamedTextColor.GRAY))
                .append(Component.join(separator, components.asJava))
                .build()
            player.sendMessage(listMsg)
        }
    }
}
