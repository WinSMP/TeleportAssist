package org.winlogon.teleportassist

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.{Bukkit, Location}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap

class TpaHandler(tpaPlugin: TpaPlugin) {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  private val tpaNormalRequests = TrieMap.empty[Player, Player]
  private val tpaHereRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  val isFolia = Utilities.detectFolia()

  /**
   * Register all teleport-related commands using CommandAPI
   */
  def registerCommands(): Unit = {
    // /tpa <player>
    new CommandAPICommand("tpa")
      .withArguments(new PlayerArgument("target"))
      .executesPlayer((player, args) => {
        val target = args.get("target").asInstanceOf[Player]
        tpaCommand(player, target)
      })
      .register()

    // /tpaccept [player]
    new CommandAPICommand("tpaccept")
      .withOptionalArguments(new PlayerArgument("player"))
      .executesPlayer((player, args) => {
        val targetOption = Option(args.getOrDefault("player", null).asInstanceOf[Player])
        tpAcceptCommand(player, targetOption.map(_.getName))
      })
      .register()

    // /tpdeny
    new CommandAPICommand("tpdeny")
      .executesPlayer((player, _) => {
        tpDenyCommand(player)
      })
      .register()

    // /tpahere <player>
    new CommandAPICommand("tpahere")
      .withArguments(new PlayerArgument("target"))
      .executesPlayer((player, args) => {
        val target = args.get("target").asInstanceOf[Player]
        tpAHereCommand(player, target)
      })
      .register()

    // /back
    new CommandAPICommand("back")
      .executesPlayer((player, _) => {
        backCommand(player)
      })
      .register()
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
    * @param target The target player who will receive the request
    * @return
    */
  private def tpaCommand(player: Player, target: Player): Boolean = {
    if (target != null && target.isOnline) {
      tpaNormalRequests.put(target, player)
      sendMessage(target, s"§3${player.getName}§7 wants to teleport to you.", messages(0))
      sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
      return true
    } else {
      sendMessage(player, s"§7Player not found.")
      return false
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
            return false
        }
      case None =>
        // Try to find a pending request
        tpaNormalRequests.find(_._1 == player).orElse(tpaHereRequests.find(_._2 == player)) match {
          case Some((requester, _)) =>
            val tpType = if (tpaNormalRequests.contains(player)) TeleportType.Normal else TeleportType.Here
            acceptRequest(player, requester, tpType)
          case None =>
            player.sendMessage(messages(1))
            return false
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
        teleportAsync(requester, player.getLocation, s"§7Teleported to §3${player.getName}.", s"§3${requester.getName} §7teleported to you.")
      case TeleportType.Here =>
        teleportAsync(player, requester.getLocation, s"§7Teleported to §3${requester.getName}.", s"§3${player.getName} §7teleported to you.")
    }
    tpaNormalRequests.remove(requester)
    tpaHereRequests.remove(player)
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
    * @param target The target player
    * @return
    */
  private def tpAHereCommand(player: Player, target: Player): Boolean = {
    if (target != null && target.isOnline) {
      tpaHereRequests.put(player, target)
      sendMessage(target, s"§3${player.getName}§7 wants you to teleport to them.", "§7Type §3/tpaccept§7 to accept or §3/tpdeny§7 to deny.")
      sendMessage(player, s"§7Teleport request sent to §3${target.getName}.")
      return true
    } else {
      sendMessage(player, s"§7Player not found.")
      return false
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

  private def sendMessage(player: Player, messages: String*): Unit = messages.foreach(player.sendMessage)

  sealed trait TeleportType
  object TeleportType {
    case object Normal extends TeleportType
    case object Here extends TeleportType
  }
}
