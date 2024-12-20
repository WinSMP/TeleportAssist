package org.winlogon

import org.bukkit.command.{Command, CommandSender, CommandExecutor}
import org.bukkit.{Bukkit, Location}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap

class TpaHandler(tpaPlugin: TpaPlugin) extends CommandExecutor {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  private val tpaRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  private val tpahereRequests = TrieMap.empty[Player, Player]
  val isFolia = tpaPlugin.isFolia

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) return false

    val player = sender.asInstanceOf[Player]

    // Ensure there is at least one argument
    if (args.isEmpty || args(0) == "help") {
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
    if (isFolia) {
      val regionScheduler = player.getServer.getRegionScheduler
      regionScheduler.execute(tpaPlugin, location, new Runnable { def run(): Unit = task() })
    } else {
      Bukkit.getScheduler.runTask(tpaPlugin, new Runnable { def run(): Unit = task() })
    }
  }

  /**
    * Teleport a player asynchronously to a location with 
    * optional messages using the provided scheduler.
    *
    * @param player The player to teleport
    * @param location The location to teleport to
    * @param successMessage The message to display on success
    * @param notifyMessage The message to notify the player with
    */
  private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
    if (isFolia) {
      val teleportTask = new Runnable {
        override def run(): Unit = {
          player.teleportAsync(location).thenAccept(_ => {
            if (successMessage.nonEmpty) player.sendMessage(successMessage)
            if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
          })
        }
      }
  
      val emptyTask = new Runnable {
        override def run(): Unit = {}
      }
  
      val entityScheduler = player.getScheduler
      entityScheduler.execute(tpaPlugin, teleportTask, emptyTask, 0L)
    } else {
      val syncTeleportTask = new Runnable {
        override def run(): Unit = {
          player.teleport(location)
          if (successMessage.nonEmpty) player.sendMessage(successMessage)
          if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
        }
      }
      Bukkit.getScheduler.runTaskAsynchronously(tpaPlugin, syncTeleportTask)
    }
  }

  /**
    * Send a teleport request to a player
    *
    * @param player The requester player
    * @param targetName The target player who will receive the request
    * @return
    */
  private def tpaCommand(player: Player, targetName: String): Boolean = {
    getPlayer(targetName) match {
      case Some(target) =>
        tpaRequests.put(target, player)
        sendMessage(target, s"§3${player.getName}§7 wants to teleport to you.", messages(0))
        sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
        true
      case None =>
        sendMessage(player, s"§7Player §3$targetName§7 not found.")
        false
    }
  }

  /**
    * Accept a teleport request
    *
    * @param player The player accepting the request
    * @param requesterName The player who sent the request
    * @return
    */
  private def tpAcceptCommand(player: Player, requesterName: Option[String]): Boolean = {
    requesterName match {
      case Some(name) =>
        val requester = Bukkit.getPlayer(name)
        if (requester != null && tpaRequests.get(player).contains(requester)) {
          acceptRequest(player, requester)
        } else {
          player.sendMessage(s"§7No teleport request from §3$name§7.")
        }
      case None =>
        tpaRequests.get(player) match {
          case Some(requester) => acceptRequest(player, requester)
          case None => player.sendMessage(messages(1))
        }
    }
    true
  }

  /**
    * Accept a teleport request and teleport
    *
    * @param player The player accepting the request
    * @param requester The player who sent the request
    */
  private def acceptRequest(player: Player, requester: Player): Unit = {
    playerLocations.put(requester, requester.getLocation)
    teleportAsync(requester, player.getLocation, s"§7Teleported to §3${player.getName}.", s"§3${requester.getName} §7teleported to you.")
    tpaRequests.remove(player)
  }

  /**
    * Deny a teleport request
    *
    * @param player The player denying the request
    * @return
    */
  private def tpDenyCommand(player: Player): Boolean = {
    tpaRequests.get(player) match {
      case Some(requester) =>
        sendMessage(requester, s"§7Teleport request §cdenied§7 by §3${player.getName}.")
        sendMessage(player, messages(2))
        tpaRequests.remove(player)
      case None =>
        sendMessage(player, messages(1))
    }
    true
  }

  /**
    * Teleport a player to you
    *
    * @param player The requester player
    * @param targetName The target player
    * @return
    */
  private def tpAHereCommand(player: Player, targetName: String): Boolean = {
    getPlayer(targetName) match {
      case Some(target) =>
        tpahereRequests.put(player, target)
        sendMessage(target, s"§3${player.getName}§7 wants you to teleport to them.", "§7Type §3/tpaccept§7 to accept or §3/tpdeny§7 to deny.")
        sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
        true
      case None =>
        sendMessage(player, s"§7Player §3$targetName§7 not found.")
        false
    }
  }

  /**
    * Teleport a player to their previous location after
    * they have been teleported to you
    *
    * @param player The player teleporting back
    * @return
    */
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
}
