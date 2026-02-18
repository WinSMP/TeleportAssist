// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.entity.{EntityDamageEvent, PlayerDeathEvent}
import org.bukkit.event.player.{PlayerMoveEvent, PlayerQuitEvent}

class PlayerListener(
    tpaHandler: TpaHandler,
    database: Database,
    scheduler: Scheduler
) extends Listener {

    /** Cleans up warmup and pending requests when a player leaves. */
    @EventHandler
    def onPlayerQuit(event: PlayerQuitEvent): Unit = {
        val player = event.getPlayer
        scheduler.removePlayer(player)
        tpaHandler.removePlayer(player)
    }

    /** Saves the death location to the database for /back. */
    @EventHandler
    def onPlayerDeath(event: PlayerDeathEvent): Unit = {
        database.saveDeathLocation(event.getPlayer.getUniqueId, event.getPlayer.getLocation)
    }

    /** Cancels warmup if the player moves more than a negligible amount. */
    @EventHandler
    def onPlayerMove(event: PlayerMoveEvent): Unit = {
        val to = event.getTo
        if (to == null) return
        val from = event.getFrom
        if (from.getBlockX == to.getBlockX &&
            from.getBlockY == to.getBlockY &&
            from.getBlockZ == to.getBlockZ) return
        val player = event.getPlayer
        if (scheduler.hasWarmup(player))
            scheduler.checkMovement(player)
    }

    /** Cancels warmup when a player takes damage. */
    @EventHandler
    def onPlayerDamage(event: EntityDamageEvent): Unit = {
        event.getEntity match {
            case player: org.bukkit.entity.Player =>
                scheduler.cancelWarmup(player, Messages.Error.WarmupDamage)
            case _ =>
        }
    }
}
