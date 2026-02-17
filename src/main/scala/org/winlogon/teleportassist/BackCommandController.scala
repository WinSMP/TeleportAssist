// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command

import io.papermc.paper.command.brigadier.{CommandSourceStack, Commands}
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

import org.bukkit.{Bukkit, Location}
import org.bukkit.entity.Player

import Messages.sendBuiltinMessage

class BackCommandController(
    plugin: TeleportAssist,
    teleportService: TeleportService,
    database: Database,
    scheduler: Scheduler
) {
    private val backExecutor: Command[CommandSourceStack] = ctx => {
        ctx.getSource.getSender match {
            case player: Player => handleBack(player)
            case _ =>
        }
        Command.SINGLE_SUCCESS
    }

    private val backCommand = Commands.literal("back")
        .requires(src => src.getSender.isInstanceOf[Player])
        .executes(backExecutor)
        .build()

    private val tpBackCommand = Commands.literal("tpback")
        .requires(src => src.getSender.isInstanceOf[Player])
        .executes(backExecutor)
        .build()

    plugin.getLifecycleManager.registerEventHandler(
        LifecycleEvents.COMMANDS,
        (event: ReloadableRegistrarEvent[Commands]) => {
            val r = event.registrar()
            r.register(backCommand, "Go back to your previous or death location")
            r.register(tpBackCommand, "Go back to where you were before teleporting")
        }
    )

    private def handleBack(player: Player): Unit = {
        resolveBackLocation(player) match {
            case Some(loc) =>
                teleportBackTo(player, loc)
            case None =>
                player.sendBuiltinMessage(Messages.Notice.NoPreviousLocation)
        }
    }

    private def resolveBackLocation(player: Player): Option[Location] = {
        teleportService.playerLocations.get(player) match {
            case loc: Location =>
                Some(loc)

            case null =>
                database.getLatestDeathLocation(player.getUniqueId).flatMap { deathLocation =>
                    Option(Bukkit.getWorld(deathLocation.world)).map { world =>
                        Location(
                            world, deathLocation.x, deathLocation.y, deathLocation.z,
                            deathLocation.yaw, deathLocation.pitch
                        )
                    }
                }
        }
    }

    private def teleportBackTo(player: Player, loc: Location): Unit = {
        teleportService.playerLocations.put(player, player.getLocation)
        teleportService.teleportWithWarmup(
            player,
            loc,
            Messages.format(Messages.Notice.TeleportBackSuccess),
            scheduler = scheduler
        )
    }
}
