package org.winlogon

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
                    case Array("tpa", targetName) => tpaCommand(player, targetName)
                    case Array("tpaccept") => tpAcceptCommand(player, None)
                    case Array("tpaccept", requesterName) => tpAcceptCommand(player, Some(requesterName))
                    case Array("tpdeny") => tpDenyCommand(player)
                    case Array("tpahere", targetName) => tpAHereCommand(player, targetName)
                    case Array("back") => backCommand(player)
                    case _ => false
                }
            case _ => false
        }
    }

    private def tpaCommand(player: Player, targetName: String): Boolean = {
        val target = Bukkit.getPlayer(targetName)
        if (target == null) {
            player.sendMessage(s"Player $targetName not found.")
            false
        }
        tpaRequests += (target -> player)
        target.sendMessage(s"${player.getName} wants to teleport to you.")
        target.sendMessage(s"Type /tpaccept to accept or /tpdeny to deny.")
        player.sendMessage(s"Teleport request sent to ${target.getName}.")
        true
    }

    private def tpAcceptCommand(player: Player, requesterName: Option[String]): Boolean = {
        requesterName match {
            case Some(name) =>
                val requester = Bukkit.getPlayer(name)
                if (requester != null && tpaRequests.get(player).contains(requester)) {
                    acceptRequest(player, requester)
                } else {
                    player.sendMessage(s"No teleport request from $name.")
                }
            case None =>
                tpaRequests.get(player) match {
                    case Some(requester) => acceptRequest(player, requester)
                    case None => player.sendMessage("No teleport request pending.")
                }
        }
        true
    }

    private def acceptRequest(player: Player, requester: Player): Unit = {
        playerLocations += (requester -> requester.getLocation)
        requester.teleport(player)
        requester.sendMessage(s"Teleported to ${player.getName}.")
        player.sendMessage(s"${requester.getName} teleported to you.")
        tpaRequests -= player
    }

    private def tpDenyCommand(player: Player): Boolean = {
        tpaRequests.get(player) match {
            case Some(requester) =>
                requester.sendMessage(s"Teleport request denied by ${player.getName}.")
                player.sendMessage(s"Teleport request denied.")
                tpaRequests -= player
            case None =>
                player.sendMessage("No teleport request pending.")
        }
        true
    }

    private def tpAHereCommand(player: Player, targetName: String): Boolean = {
        Option(Bukkit.getPlayer(targetName)) match {
          case Some(player) =>
            target.teleport(player)
            target.sendMessage(s"Teleported to ${player.getName}.")
            player.sendMessage(s"${target.getName} teleported to you.")
          case None =>
            player.sendMessage(s"Player $targetName not found.")
        }
        true
    }

    private def backCommand(player: Player): Boolean = {
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
