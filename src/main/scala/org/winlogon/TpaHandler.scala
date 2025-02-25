package org.winlogon

import org.bukkit.command.{Command, CommandSender, CommandExecutor}
import org.bukkit.{Bukkit, Location}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap

class TpaHandler(tpaPlugin: TpaPlugin) extends CommandExecutor {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  private val tpaNormalRequests = TrieMap.empty[Player, Player]
  private val tpaHereRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  val isFolia = Utilities.detectFolia()

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    if (!sender.isInstanceOf[Player]) return false

    val player = sender.asInstanceOf[Player]
    val standaloneCommands = List("back", "tpdeny", "tpcancel")

    // Ensure there is at least one argument
    if (args.isEmpty || args(0) == "help" && !standaloneCommands.contains(label)) {
      player.sendMessage(errorMessages(0))
      return false
    }

    label match {
      case "back"                            => backCommand(player)
      case "tpdeny"                          => tpDenyCommand(player)
      case "tpcancel"                        => tpCancelCommand(player)
      case "tpa" if args.length == 1         => tpaCommand(player, args(0))
      case "tpaccept" if args.length == 1    => tpAcceptCommand(player, Some(args(0)))
      case "tpahere" if args.length == 1     => tpAHereCommand(player, args(0))
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
      val entityScheduler = player.getScheduler
      entityScheduler.execute(tpaPlugin, new Runnable {
        override def run(): Unit = {
          player.teleportAsync(location).thenAccept(_ => {
            if (successMessage.nonEmpty) player.sendMessage(successMessage)
            if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
          })
        }
      }, new Runnable {
        override def run(): Unit = {}
      }, 0L)
    } else {
      Bukkit.getScheduler.runTaskAsynchronously(tpaPlugin, new Runnable {
        override def run(): Unit = {
          player.teleport(location)
          if (successMessage.nonEmpty) player.sendMessage(successMessage)
          if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
        }
      })
    }
  }

  /** Send a teleport request to a player
    *
    * @param player The requester player
    * @param targetName The target player who will receive the request
    * @return
    */
   private def tpaCommand(player: Player, targetName: String): Boolean = {
    getPlayer(targetName) match {
      case Some(target) =>
        // Check for existing requests
        if (tpaNormalRequests.get(target).contains(player) || tpaHereRequests.get(player).contains(target)) {
          sendMessage(player, s"§7You already have a pending request to §3${target.getName}§7.")
          return true
        }
        
        tpaNormalRequests.put(target, player)
        sendMessage(target, s"§3${player.getName}§7 wants to teleport to you.", messages(0))
        sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
        true
      case None =>
        sendMessage(player, s"§7Player §3$targetName§7 not found.")
        true
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
        requester match {
          case p: Player if tpaNormalRequests.get(player).contains(p) =>
            acceptRequest(player, p, TeleportType.Normal)
          case p: Player if tpaHereRequests.get(p).contains(player) =>
            acceptRequest(player, p, TeleportType.Here)
          case _ =>
            player.sendMessage(s"§7No teleport request from §3$name§7.")
            return true
        }
      case None =>
        tpaNormalRequests.find(_._1 == player).orElse(tpaHereRequests.find(_._2 == player)) match {
          case Some((requester, _)) =>
            val tpType = if (tpaNormalRequests.contains(player)) TeleportType.Normal else TeleportType.Here
            acceptRequest(player, requester, tpType)
          case None =>
            player.sendMessage(messages(1))
            return true
        }
    }
    true
  }

  /**
    * Accept a teleport request and teleport
    *
    * @param player The player accepting the request
    * @param requester The player who sent the request
    * @param tpType The teleport type (Normal/Here)
    */
   private def acceptRequest(player: Player, requester: Player, tpType: TeleportType): Unit = {
    playerLocations.put(requester, requester.getLocation)
    tpType match {
      case TeleportType.Normal =>
        teleportAsync(requester, player.getLocation,s"§7Teleported to §3${player.getName}.", s"§3${requester.getName} §7teleported to you.")
        tpaNormalRequests.remove(player) // Remove by target (player)
      case TeleportType.Here =>
        teleportAsync(player, requester.getLocation, s"§3${player.getName} §7teleported to you.", s"§7Teleported to §3${requester.getName}.")
        tpaHereRequests.remove(requester) // Remove by requester
    }
  }

  /**
    * Deny a teleport request
    *
    * @param player The player denying the request
    * @return
    */
  private def tpDenyCommand(player: Player): Boolean = {
    tpaNormalRequests.find(_._2 == player).orElse(tpaHereRequests.find(_._1 == player)) match {
      case Some((requester, _)) =>
        sendMessage(requester, s"§7Teleport request §cdenied§7 by §3${player.getName}.")
        sendMessage(player, messages(2))
        tpaNormalRequests.remove(requester)
        tpaHereRequests.remove(player)
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
        // Check for existing requests
        if (tpaNormalRequests.get(target).contains(player) || tpaHereRequests.get(player).contains(target)) {
          sendMessage(player, s"§7You already have a pending request to §3${target.getName}§7.")
          return true
        }
        
        tpaHereRequests.put(player, target)
        sendMessage(target, s"§3${player.getName}§7 wants you to teleport to them.", "§7Type §3/tpaccept§7 to accept or §3/tpdeny§7 to deny.")
        sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
        true
      case None =>
        sendMessage(player, s"§7Player §3$targetName§7 not found.")
        true
    }
  }

  private def tpCancelCommand(player: Player): Boolean = {
    // Cancel outgoing TPA requests (where player is requester)
    val normalCancelled = tpaNormalRequests.filter { case (_, r) => r == player }.keys.toList
    normalCancelled.foreach(tpaNormalRequests.remove)

    // Cancel outgoing TPAHere requests (where player is requester)
    val hereCancelled = tpaHereRequests.filter { case (r, _) => r == player }.keys.toList
    hereCancelled.foreach(tpaHereRequests.remove)

    if (normalCancelled.nonEmpty || hereCancelled.nonEmpty) {
      sendMessage(player, "§7All your teleport requests have been cancelled.")
    } else {
      sendMessage(player, "§7You have no pending teleport requests to cancel.")
    }
    true
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
        teleportAsync(player, location, "§7Teleported back to previous location.", "")
        playerLocations.remove(player)
        true
      case None =>
        sendMessage(player, "§7No previous location to teleport back to.")
        true
    }
  }

  private def getPlayer(name: String): Option[Player] = Option(Bukkit.getPlayerExact(name)).filter(_.isOnline)

  private def sendMessage(player: Player, messages: String*): Unit = messages.foreach(player.sendMessage)

  sealed trait TeleportType
  object TeleportType {
    case object Normal extends TeleportType
    case object Here extends TeleportType
  }
}
