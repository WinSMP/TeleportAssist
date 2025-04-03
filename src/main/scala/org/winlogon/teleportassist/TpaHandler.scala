package org.winlogon.teleportassist

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.executors.{PlayerCommandExecutor, CommandArguments}
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.{Bukkit, Location}
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

import scala.collection.concurrent.TrieMap
import java.util.function.Supplier

class TpaHandler(tpaPlugin: TeleportAssist) {
  private val tpaNormalRequests = TrieMap.empty[Player, Player]
  private val tpaHereRequests = TrieMap.empty[Player, Player]
  private val playerLocations = TrieMap.empty[Player, Location]
  val isFolia = Utilities.detectFolia()

  private val mm = MiniMessage.miniMessage()
  private val tagResolver = TagResolver.builder()
    .resolver(TagResolver.standard())
    .build()

  /**
   * Register all teleport-related commands using CommandAPI
   */
  def registerCommands(): Unit = {
    new CommandAPICommand("tpa")
      .withArguments(new PlayerArgument("target"))
      .executesPlayer(new PlayerCommandExecutor {
        override def run(player: Player, args: CommandArguments): Unit = {
          val target = args.get("target").asInstanceOf[Player]
          tpaCommand(player, target)
        }
      })
      .register()

    new CommandAPICommand("tpaccept")
      .withOptionalArguments(new PlayerArgument("player"))
      .executesPlayer(new PlayerCommandExecutor {
        override def run(player: Player, args: CommandArguments): Unit = {
          val targetOption = try {
            Option(args.get("player").asInstanceOf[Player])
          } catch {
            case _: Exception => None
          }
          tpAcceptCommand(player, targetOption.map(_.getName))
        }
     })
      .register()

    new CommandAPICommand("tpdeny")
      .withOptionalArguments(new PlayerArgument("player"))
      .executesPlayer(new PlayerCommandExecutor {
        override def run(player: Player, args: CommandArguments): Unit = {
          val targetOption = try {
            Option(args.get("player").asInstanceOf[Player])
          } catch {
            case _: Exception => None
          }
          tpaDenyCommand(player, targetOption.map(_.getName))
        }
      })
      .register()

    new CommandAPICommand("tpahere")
      .withArguments(new PlayerArgument("target"))
      .executesPlayer(new PlayerCommandExecutor {
        override def run(player: Player, args: CommandArguments): Unit = {
          val target = args.get("target").asInstanceOf[Player]
          tpaHereCommand(player, target)
        }
      })
      .register()

    new CommandAPICommand("back")
      .executesPlayer(new PlayerCommandExecutor {
        override def run(player: Player, args: CommandArguments): Unit = {
          backCommand(player)
        }
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
    */
  private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
    if (isFolia) {
      val entityScheduler = player.getScheduler
      entityScheduler.execute(tpaPlugin, new Runnable {
        override def run(): Unit = {
          player.teleportAsync(location).thenAccept(_ => {
            if (successMessage.nonEmpty) sendComponent(player, successMessage)
            if (notifyMessage.nonEmpty) sendComponent(player, notifyMessage)
          })
        }
      }, new Runnable {
        override def run(): Unit = {}
      }, 0L)
    } else {
      Bukkit.getScheduler.runTaskAsynchronously(tpaPlugin, new Runnable {
        override def run(): Unit = {
          player.teleport(location)
          if (successMessage.nonEmpty) sendComponent(player, successMessage)
          if (notifyMessage.nonEmpty) sendComponent(player, notifyMessage)
        }
      })
    }
  }

  private def tpaCommand(player: Player, target: Player): Boolean = {
    if (target == null || !target.isOnline) {
      sendComponent(player, Messages.Error.PlayerNotFound)
      return false
    }

    if (player == target) {
      sendComponent(player, Messages.Error.CannotTeleportSelf)
      return false
    }

    if (tpaNormalRequests.get(target).contains(player)) {
      sendComponent(player, Messages.Error.RequestAlreadyPending)
      return false
    }

    tpaNormalRequests.put(target, player)
    val requestMsg = s"<aqua>${player.getName}</aqua> <gray>wants to teleport to you. " +
      s"<click:run_command:'/tpaccept ${player.getName}'><green>[Accept]</green></click> " +
      s"<click:run_command:'/tpdeny ${player.getName}'><red>[Deny]</red></click>"
    sendComponent(target, requestMsg)
    sendComponent(player, Messages.Notice.TpaRequestSent.replace("<target>", target.getName))
    true
  }

  private def tpAcceptCommand(player: Player, requesterName: Option[String]): Boolean = {
    requesterName match {
      case Some(name) =>
        val requester = Bukkit.getPlayer(name)
        if (requester == null || !requester.isOnline) {
          sendComponent(player, Messages.Error.PlayerNotOnline.replace("<player>", name))
          return false
        }

        if (tpaNormalRequests.get(player).contains(requester)) {
          acceptRequest(player, requester, TeleportType.Normal)
          true
        } else if (tpaHereRequests.get(requester).contains(player)) {
          acceptRequest(player, requester, TeleportType.Here)
          true
        } else {
          sendComponent(player, Messages.Error.PlayerNotOnline.replace("<player>", name))
          false
        }
      case None =>
        tpaNormalRequests.find(_._1 == player) match {
          case Some((_, requester)) =>
            acceptRequest(player, requester, TeleportType.Normal)
            true
          case None =>
            tpaHereRequests.find(_._2 == player) match {
              case Some((requester, _)) =>
                acceptRequest(player, requester, TeleportType.Here)
                true
              case None =>
                sendComponent(player, Messages.Notice.NoPendingRequest)
                false
            }
        }
    }
  }

  private def acceptRequest(player: Player, requester: Player, tpType: TeleportType): Unit = {
    playerLocations.put(requester, requester.getLocation)
    tpType match {
      case TeleportType.Normal =>
        tpaNormalRequests.remove(player)
        teleportAsync(
          requester,
          player.getLocation,
          Messages.Notice.TeleportSuccess.replace("<player>", player.getName),
          Messages.Notice.TeleportHereSuccess.replace("<player>", requester.getName)
        )
      case TeleportType.Here =>
        tpaHereRequests.remove(requester)
        teleportAsync(
          player,
          requester.getLocation,
          Messages.Notice.TeleportHereSuccess.replace("<player>", requester.getName),
          Messages.Notice.TeleportSuccess.replace("<player>", player.getName)
        )
    }
  }

  private def tpaDenyCommand(player: Player, requesterName: Option[String]): Boolean = {
    requesterName match {
      case Some(name) =>
        val requester = Bukkit.getPlayer(name)
        if (requester == null || !requester.isOnline) {
          sendComponent(player, Messages.Error.PlayerNotOnline.replace("<player>", name))
          return false
        }

        if (tpaNormalRequests.get(player).contains(requester)) {
          sendComponent(requester, s"<gray>Your teleport request to <aqua>${player.getName}</aqua> was <red>denied</red>.")
          sendComponent(player, Messages.Notice.TeleportDenied)
          tpaNormalRequests.remove(player)
          true
        } else if (tpaHereRequests.get(requester).contains(player)) {
          sendComponent(requester, s"<gray>Your teleport request to <aqua>${player.getName}</aqua> was <red>denied</red>.")
          sendComponent(player, Messages.Notice.TeleportDenied)
          tpaHereRequests.remove(requester)
          true
        } else {
          sendComponent(player, Messages.Notice.NoPendingRequest)
          false
        }
      case None =>
        tpaNormalRequests.find(_._1 == player) match {
          case Some((_, requester)) =>
            sendComponent(requester, s"<gray>Your teleport request to <aqua>${player.getName}</aqua> was <red>denied</red>.")
            sendComponent(player, Messages.Notice.TeleportDenied)
            tpaNormalRequests.remove(player)
            true
          case None =>
            tpaHereRequests.find(_._2 == player) match {
              case Some((requester, _)) =>
                sendComponent(requester, s"<gray>Your teleport request to <aqua>${player.getName}</aqua> was <red>denied</red>.")
                sendComponent(player, Messages.Notice.TeleportDenied)
                tpaHereRequests.remove(requester)
                true
              case None =>
                sendComponent(player, Messages.Notice.NoPendingRequest)
                false
            }
        }
    }
  }

  private def tpaHereCommand(player: Player, target: Player): Boolean = {
    if (target == null || !target.isOnline) {
      sendComponent(player, Messages.Error.PlayerNotFound)
      return false
    }

    if (player == target) {
      sendComponent(player, Messages.Error.CannotTeleportSelfHere)
      return false
    }

    if (tpaHereRequests.get(player).exists(_ == target)) {
      sendComponent(player, Messages.Error.RequestAlreadyPending)
      return false
    }

    tpaHereRequests.put(player, target)
    val requestMsg = s"""
        |<aqua>${player.getName}</aqua> <gray>wants you to teleport to them.
        |<click:run_command:'/tpaccept ${player.getName}'><green>[Accept]</green></click>
        |<click:run_command:'/tpdeny ${player.getName}'><red>[Deny]</red></click>
        |""".stripMargin
    sendComponent(target, requestMsg)
    sendComponent(player, Messages.Notice.TpaHereRequestSent.replace("<target>", target.getName))
    true
  }

  private def backCommand(player: Player): Boolean = {
    playerLocations.get(player) match {
      case Some(location) =>
        teleportAsync(player, location, Messages.Notice.TeleportBackSuccess, "")
        playerLocations.remove(player)
        true
      case None =>
        sendComponent(player, Messages.Notice.NoPreviousLocation)
        false
    }
  }

  private def sendComponent(player: Player, message: String): Unit = {
    player.sendMessage(mm.deserialize(message, tagResolver))
  }

  sealed trait TeleportType
  object TeleportType {
    case object Normal extends TeleportType
    case object Here extends TeleportType
  }
}
