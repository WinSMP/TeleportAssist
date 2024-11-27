package org.winlogon
// issues:
// maven or sbt?
// /tpaaccept by default accept latest tpa requests, /tpaaccept <player> accepts request from player
// clickable links to /tpa, /tpaccept, /tpdeny, /tpahere, /back

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.mutable

class TpaPlugin extends JavaPlugin {
    private val tpaRequests = mutable.Map.empty[Player, Player]
    private val playerLocations = mutable.Map.empty[Player, Location]

    override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
        sender match {
            case player: Player =>
                args match {
                    case Array("tpa", targetName) => handleTpaCommand(player, targetName)
                    case Array("tpaccept") => handleTpAcceptCommand(player)
                    case Array("tpdeny") => handleTpDenyCommand(player)
                    case Array("tpahere", targetName) => handleTpAHereCommand(player, targetName)
                    case Array("back") => handleBackCommand(player)
                    case _ => false
                }
                case _ => false
        }
    }

    private def handleTpaCommand(player: Player, targetName: String): Boolean = {
        val target = Bukkit.getPlayer(targetName)
        if (target != null) {
            tpaRequests += (target -> player)
            target.sendMessage(s"${player.getName} wants to teleport to you. Type /tpaccept to accept.")
            player.sendMessage(s"Teleport request sent to ${target.getName}.")
        } else {
            player.sendMessage(s"Player $targetName not found.")
        }
        true
    }

    private def handleTpRequest(player: Player, accept: Boolean): Boolean = {
        tpaRequests.get(player) match {
            case Some(requester) =>
                if (accept) {
                    playerLocations += (requester -> requester.getLocation)
                    requester.teleport(player)
                    requester.sendMessage(s"Teleported to ${player.getName}.")
                    player.sendMessage(s"${requester.getName} teleported to you.")
                } else {
                    requester.sendMessage(s"Teleport request denied by ${player.getName}.")
                    player.sendMessage(s"Teleport request denied.")
                }
                tpaRequests -= player
            case None =>
                player.sendMessage("No teleport request pending.")
        }
        true
    }

    private def handleTpAcceptCommand(player: Player): Boolean = handleTpRequest(player, accept = true)

    private def handleTpDenyCommand(player: Player): Boolean = handleTpRequest(player, accept = false)

    private def handleTpAHereCommand(player: Player, targetName: String): Boolean = {
        val target = Bukkit.getPlayer(targetName)
        if (target != null) {
            target.teleport(player)
            target.sendMessage(s"Teleported to ${player.getName}.")
            player.sendMessage(s"${target.getName} teleported to you.")
        } else {
            player.sendMessage(s"Player $targetName not found.")
        }
        true
    }

    private def handleBackCommand(player: Player): Boolean = {
        playerLocations.get(player) match {
            case Some(location) =>
                player.teleport(location)
                player.sendMessage("Teleported back to previous location.")
                playerLocations -= player
            case None =>
                player.sendMessage("No previous location to teleport back to.")
        }
        true
    }
}
