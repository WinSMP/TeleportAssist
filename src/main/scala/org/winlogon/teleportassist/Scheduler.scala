// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.winlogon.asynccraftr.AsyncCraftr

import org.bukkit.Location
import org.bukkit.entity.Player

import java.time.Duration
import scala.collection.concurrent.TrieMap

import Messages.sendBuiltinMessage

class Scheduler(plugin: TeleportAssist, val warmupSeconds: Int) {

    private val warmups = TrieMap.empty[Player, WarmupEntry]
    private val cooldowns = TrieMap.empty[Player, Long]
    private val DefaultCooldownMillis = 5000L

    def isEnabled: Boolean = warmupSeconds > 0

    def hasCooldown(player: Player): Boolean =
        cooldowns.get(player).exists(_ > System.currentTimeMillis())

    def setCooldown(player: Player, millis: Long = DefaultCooldownMillis): Unit =
        cooldowns.put(player, System.currentTimeMillis() + millis)

    def startWarmup(
        player: Player,
        destination: Location,
        onComplete: () => Unit,
        onCancel: () => Unit = () => {}
    ): Unit = {
        warmups.get(player).foreach(_.cancel())

        val entry = WarmupEntry(player.getLocation, destination, onCancel)
        warmups.put(player, entry)

        player.sendBuiltinMessage(Messages.Notice.WarmupStarted, "seconds" -> warmupSeconds.toString)

        val handler: Runnable = () => {
            warmups.remove(player) match {
                case Some(e) if !e.cancelled => onComplete()
                case _ =>
            }
        }

        AsyncCraftr.runEntityTaskLater(plugin, player, handler, Duration.ofSeconds(warmupSeconds))
    }

    def checkMovement(player: Player): Unit = {
        warmups.get(player) match {
            case Some(entry) if entry.startLocation.distanceSquared(player.getLocation) > 0.25 =>
                cancelWarmup(player)
            case _ =>
        }
    }

    def cancelWarmup(player: Player, reason: String = ""): Unit = {
        warmups.remove(player) match {
            case Some(entry) =>
                entry.cancel()
                entry.onCancel()
                if (reason.nonEmpty) player.sendBuiltinMessage(reason)
            case None =>
        }
    }

    def hasWarmup(player: Player): Boolean = warmups.contains(player)

    def removePlayer(player: Player): Unit = {
        warmups.remove(player).foreach(_.cancel())
        cooldowns.remove(player)
    }

    private case class WarmupEntry(
        startLocation: Location,
        destination: Location,
        onCancel: () => Unit
    ) {
        @volatile var cancelled = false
        def cancel(): Unit = { cancelled = true }
    }
}
