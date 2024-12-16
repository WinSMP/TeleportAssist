package org.winlogon

import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import org.bukkit.{Bukkit, Location}
import org.bukkit.command.{Command, CommandSender}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap

class TpaPlugin extends JavaPlugin {
  private val tpaRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  private val tpahereRequests = TrieMap.empty[Player, Player]
  private val errorMessages = List(
    "§7Usage: §3/tpa §2<player> §7or §3/tpaccept §2<player>",
    "§cUnknown or incomplete command. Usage: /tpa <player>, /tpaccept <player>, /tpdeny, /tpahere <player>, /back",
  )
  private val messages = List(
    "§7Type §3/tpaccept§7 to accept or §3/tpdeny§7 to deny.",
    "§7No teleport request pending.",
    "§7Teleport request §cdenied."
  )

  private var isFolia: Boolean = _

  override def onEnable(): Unit = {
    isFolia = detectFolia()
    getLogger.info(s"§7TpaPlugin started! Running on ${if (isFolia) "Folia" else "Paper"}")
  }

  override def onDisable(): Unit = {
    getLogger.info("§7TpaPlugin disabled!")
  }

  private def detectFolia(): Boolean = {
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
      true
    } catch {
      case _: ClassNotFoundException => false
    }
  }

  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
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
    if (isFolia) {
      val regionScheduler = player.getServer.getRegionScheduler
      regionScheduler.execute(this, location, new Runnable { def run(): Unit = task() })
    } else {
      Bukkit.getScheduler.runTask(this, new Runnable { def run(): Unit = task() })
    }
  }

  private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
    if (isFolia) {
      val entityScheduler = player.getScheduler
      entityScheduler.execute(this, new Runnable {
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
      Bukkit.getScheduler.runTask(this, new Runnable {
        override def run(): Unit = {
          player.teleport(location)
          if (successMessage.nonEmpty) player.sendMessage(successMessage)
          if (notifyMessage.nonEmpty) player.sendMessage(notifyMessage)
        }
      })
    }
  }

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

  private def acceptRequest(player: Player, requester: Player): Unit = {
    playerLocations.put(requester, requester.getLocation)
    teleportAsync(requester, player.getLocation, s"§7Teleported to §3${player.getName}.", s"§3${requester.getName} §7teleported to you.")
    tpaRequests.remove(player)
  }

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
