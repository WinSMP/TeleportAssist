package org.winlogon

import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import io.papermc.paper.plugin.loader.PluginLoader
import org.bukkit.{Bukkit, Location}
import org.bukkit.command.{Command, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap

class TpaPlugin extends JavaPlugin {
  private val tpaRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  private val errorMessages = List(
    "§7Usage: §3/tpa §2<player> §7or §3/tpaccept §2<player>",
    "§cUnknown or incomplete command. Usage: /tpa <player>, /tpaccept <player>, /tpdeny, /tpahere <player>, /back",
  )

  override def onEnable(): Unit = {
    getLogger.info("§7TpaPlugin started!")
  }

  override def onDisable(): Unit = {
    getLogger.info("§7TpaPlugin disabled!")
  }

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    getLogger.info(s"§7onCommand called with label: $label and args: ${args.mkString(", ")}")
    if (!sender.isInstanceOf[Player]) return false

    val player = sender.asInstanceOf[Player]

    // Ensure there is at least one argument
    if (args.isEmpty) {
      player.sendMessage(errorMessages(0))
      return false
    }

    label match {
      case "tpa" if args.length == 1 => tpaCommand(player, args(0))  // tpa <player>
      case "tpaccept" if args.length == 1 => tpAcceptCommand(player, Some(args(0)))  // tpaccept <player>
      case "tpdeny" => tpDenyCommand(player)
      case "tpahere" if args.length == 1 => tpAHereCommand(player, args(0))  // tpahere <player>
      case "back" => backCommand(player)
      case _ =>
        player.sendMessage(errorMessages(1))
        false
    }
  }

  private def executeTaskAsync(player: Player, location: Location, task: () => Unit): Unit = {
    val world = location.getWorld
    val chunkX = location.getChunk.getX
    val chunkZ = location.getChunk.getZ
  
    val regionScheduler = player.getServer.getRegionScheduler
    regionScheduler.execute(this, world, chunkX, chunkZ, () => {
      task()
    })
  }

  private def tpaCommand(player: Player, targetName: String): Boolean = {
    getPlayer(targetName) match {
      case Some(target) =>
        tpaRequests.put(target, player)
        sendMessage(target, s"§3${player.getName}§7 wants to teleport to you.", "§7Type §3/tpaccept§7 to accept or §3/tpdeny§7 to deny.")
        sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
        true
      case None =>
        sendMessage(player, s"§7Player §3$targetName§7 not found.")
        false
    }
  }

  private def tpAcceptCommand(player: Player, requesterName: Option[String]): Boolean = {
    val requester = requesterName.flatMap(getPlayer).orElse(tpaRequests.get(player))
    requester match {
      case Some(r) =>
        acceptRequest(player, r)
        true
      case None =>
        sendMessage(player, "§7No teleport request pending.")
        false
    }
  }

  private def acceptRequest(player: Player, requester: Player): Unit = {
    playerLocations.put(requester, requester.getLocation)
    teleportAsync(requester, player.getLocation, s"§7Teleported to §3${player.getName}.", s"§3${requester.getName} §7teleported to you.")
    tpaRequests.remove(player)
  }

  private def tpDenyCommand(player: Player): Boolean = {
    tpaRequests.get(player) match {
      case Some(requester) =>
        sendMessage(requester, s"§7Teleport request denied by §3${player.getName}.")
        sendMessage(player, "§7Teleport request denied.")
        tpaRequests.remove(player)
        true
      case None =>
        sendMessage(player, "§7No teleport request pending.")
        false
    }
  }

  private def tpAHereCommand(player: Player, targetName: String): Boolean = {
    getPlayer(targetName) match {
      case Some(target) =>
        playerLocations.put(target, target.getLocation)
        teleportAsync(target, player.getLocation, s"Teleported to ${player.getName}.", s"${target.getName} teleported to you.")
        true
      case None =>
        sendMessage(player, s"Player $targetName not found.")
        false
    }
  }

  private def backCommand(player: Player): Boolean = {
    playerLocations.get(player) match {
      case Some(location) =>
        teleportAsync(player, location, "Teleported back to previous location.", "")
        playerLocations.remove(player)
        true
      case None =>
        sendMessage(player, "No previous location to teleport back to.")
        false
    }
  }

  private def getPlayer(name: String): Option[Player] = Option(Bukkit.getPlayerExact(name)).filter(_.isOnline)

  private def sendMessage(player: Player, messages: String*): Unit = messages.foreach(player.sendMessage)

  private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
    executeTaskAsync(player, location, () => {
      player.teleportAsync(location).thenAccept(_ => {
        if (successMessage.nonEmpty) player.sendMessage(successMessage)
        if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
      })
    })
  }
}
