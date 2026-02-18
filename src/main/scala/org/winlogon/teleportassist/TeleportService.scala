// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.winlogon.asynccraftr.AsyncCraftr

import net.kyori.adventure.text.Component

import org.bukkit.entity.Player
import org.bukkit.Location

import java.util.concurrent.ConcurrentHashMap

class TeleportService(plugin: TeleportAssist) {
    /** Teleports a player asynchronously, sending success/notify messages on arrival. */
    def teleportAsync(
        teleportingPlayer: Player,
        location: Location,
        successMessage: Component = null,
        notifyPlayer: Player = null,
        notifyMessage: Component = null
    ): Unit = {
        AsyncCraftr.runEntityTask(plugin, teleportingPlayer, () => {
            teleportingPlayer.teleportAsync(location).thenAccept(_ => {
                if (successMessage != null) teleportingPlayer.sendMessage(successMessage)
                if (notifyMessage != null && notifyPlayer != null) {
                    notifyPlayer.sendMessage(notifyMessage)
                }
            })
        })
    }

    /** Teleports a player with configurable warmup, bypassable via permission. */
    def teleportWithWarmup(
        player: Player,
        destination: Location,
        successMessage: Component = null,
        notifyPlayer: Player = null,
        notifyMessage: Component = null,
        scheduler: Scheduler
    ): Unit = {
        playerLocations.put(player, player.getLocation)

        if (scheduler.isEnabled && !player.hasPermission("teleportassist.warmup.bypass")) {
            scheduler.startWarmup(player, destination, () =>
                teleportAsync(player, destination, successMessage, notifyPlayer, notifyMessage)
            )
        } else {
            teleportAsync(player, destination, successMessage, notifyPlayer, notifyMessage)
        }
    }

    val playerLocations = new ConcurrentHashMap[Player, Location]()
}
