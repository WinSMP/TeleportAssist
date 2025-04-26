// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.executors.{CommandArguments, CommandExecutor, PlayerCommandExecutor}

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.{TagResolver, Placeholder}

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Location}

import scala.collection.concurrent.TrieMap
import scala.jdk.OptionConverters._

class TpaHandler(tpaAssist: TeleportAssist) {

    private val tpaNormalRequests = TrieMap.empty[Player, Player]
    private val tpaHereRequests = TrieMap.empty[Player, Player]
    private val playerLocations = TrieMap.empty[Player, Location]
    val isFolia = Utilities.detectFolia()

    def registerCommands(): Unit = {
        CommandAPICommand("tpa")
            .withArguments(new PlayerArgument("target"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                tpaCommand(player, args.get("target").asInstanceOf[Player])
                ()
            }).register()

        CommandAPICommand("tpaccept")
            .withOptionalArguments(new PlayerArgument("player"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                val maybeRaw: Any = args.get("player")
                val target: Option[Player] = Option(maybeRaw.asInstanceOf[Player])
                tpAcceptCommand(player, target.map(_.getName))
                ()
            })
            .register()


        CommandAPICommand("tpdeny")
            .withOptionalArguments(new PlayerArgument("player"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                val maybeRaw: Any = args.get("player")
                val targetOption: Option[Player] = Option(maybeRaw.asInstanceOf[Player])
                tpaDenyCommand(player, targetOption.map(_.getName))
                ()
            }).register()

        CommandAPICommand("tpahere")
            .withArguments(new PlayerArgument("target"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                tpaHereCommand(player, args.get("target").asInstanceOf[Player])
                ()
            }).register()

        CommandAPICommand("back")
            .executesPlayer((player: Player, _: CommandArguments) => {
                backCommand(player)
                ()
            }).register()
    }

    private def executeTaskAsync(player: Player, location: Location, task: () => Unit): Unit = {
        if (isFolia) {
            player.getServer.getRegionScheduler.execute(tpaAssist, location, () => task())
        } else {
            Bukkit.getScheduler.runTask(tpaAssist, () => task())
        }
    }

    private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
        if (isFolia) {
            val scheduler = player.getScheduler
            scheduler.execute(tpaAssist, () => {
                player.teleportAsync(location).thenAccept(_ => {
                    if (successMessage.nonEmpty) player.sendRichMessage(successMessage)
                    if (notifyMessage.nonEmpty) player.sendRichMessage(notifyMessage)
                })
            }, () => {}, 0L)
        } else {
            Bukkit.getScheduler.runTaskAsynchronously(tpaAssist, () => {
                player.teleport(location)
                if (successMessage.nonEmpty) player.sendRichMessage(successMessage)
                if (notifyMessage.nonEmpty) player.sendRichMessage(notifyMessage)
            })
        }
    }

    private def tpaCommand(player: Player, target: Player): Boolean = {
        if (target == null || !target.isOnline) {
            player.sendRichMessage(Messages.Error.PlayerNotFound)
            return false
        }

        if (player == target) {
            player.sendRichMessage(Messages.Error.CannotTeleportSelf)
            return false
        }

        if (tpaNormalRequests.get(target).contains(player)) {
            player.sendRichMessage(Messages.Error.RequestAlreadyPending)
            return false
        }

        tpaNormalRequests.put(target, player)

        val requestMsg =
        s"""<dark_aqua><name></dark_aqua> <gray>wants to teleport to you.
        |<click:run_command:'/tpaccept ${player.getName}'><hover:show_text:'<gray>Click to accept teleport request from <dark_aqua><name></dark_aqua>'><green>[Accept]</green></hover></click>
        |<click:run_command:'/tpdeny ${player.getName}'><hover:show_text:'<gray>Click to deny teleport request from <dark_aqua><name></dark_aqua>'><red>[Deny]</red></hover></click>""".stripMargin

        target.sendRichMessage(requestMsg, Placeholder.component("name", Component.text(player.getName, NamedTextColor.DARK_AQUA)))
        player.sendRichMessage(Messages.Notice.TpaRequestSent.replace("<target>", target.getName))
        true
    }

    private def tpAcceptCommand(player: Player, requesterName: Option[String]): Boolean = {
        requesterName match {
            case Some(name) => {
                val requester = Bukkit.getPlayer(name)
                if (requester == null || !requester.isOnline) {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", name))
                    return false
                }

                if (tpaNormalRequests.get(player).contains(requester)) {
                    acceptRequest(player, requester, TeleportType.Normal)
                    true
                } else if (tpaHereRequests.get(requester).contains(player)) {
                    acceptRequest(player, requester, TeleportType.Here)
                    true
                } else {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", name))
                    false
                }
            }
            case None => {
                tpaNormalRequests.find(_._1 == player) match {
                    case Some((_, requester)) => {
                        acceptRequest(player, requester, TeleportType.Normal)
                        true
                    }
                    case None => {
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((requester, _)) => {
                                acceptRequest(player, requester, TeleportType.Here)
                                true
                            }
                            case None => {
                                player.sendRichMessage(Messages.Notice.NoPendingRequest)
                                false
                            }
                        }
                    }
                }
            }
        }
    }

    private def acceptRequest(player: Player, requester: Player, tpType: TeleportType): Unit = {
        playerLocations.put(requester, requester.getLocation)

        tpType match {
            case TeleportType.Normal => {
                tpaNormalRequests.remove(player)
                teleportAsync(
                    requester,
                    player.getLocation,
                    Messages.Notice.TeleportSuccess.replace("<player>", player.getName),
                    Messages.Notice.TeleportHereSuccess.replace("<player>", requester.getName)
                )
            }
            case TeleportType.Here => {
                tpaHereRequests.remove(requester)
                teleportAsync(
                    player,
                    requester.getLocation,
                    Messages.Notice.TeleportHereSuccess.replace("<player>", requester.getName),
                    Messages.Notice.TeleportSuccess.replace("<player>", player.getName)
                )
            }
        }
    }

    private def tpaDenyCommand(player: Player, requesterName: Option[String]): Boolean = {
        // helper to send the deny message to the requester
        def notifyRequester(r: Player): Unit = {
          r.sendRichMessage(
            "<gray>Your teleport request to <dark_aqua>" + player.getName +
            "</dark_aqua> was <red>denied</red>.</gray>"
          )
        }
    
        requesterName match {
          case Some(name) =>
            // Do we have a *normal* request TO this player from someone named `name`?
            if (tpaNormalRequests.get(player).exists(_.getName == name)) {
              val requester = Bukkit.getPlayerExact(name)
              if (requester != null && requester.isOnline) {
                notifyRequester(requester)
                tpaNormalRequests.remove(player)
                player.sendRichMessage(Messages.Notice.TeleportDenied)
                true
              } else {
                player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", name))
                false
              }
    
            // ... or do we have a *here* request FROM someone named `name` FOR this player?
            } else if (tpaHereRequests.exists { case (req, tgt) => req.getName == name && tgt == player }) {
              val requester = Bukkit.getPlayerExact(name)
              if (requester != null && requester.isOnline) {
                notifyRequester(requester)
                // remove by key
                tpaHereRequests.remove(requester)
                player.sendRichMessage(Messages.Notice.TeleportDenied)
                true
              } else {
                player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", name))
                false
              }
    
            } else {
              // no matching pending request
              player.sendRichMessage(Messages.Notice.NoPendingRequest)
              false
            }
    
          case None =>
            // no name given - deny the OLDEST pending request, preferring normal over here
            tpaNormalRequests.get(player) match {
              case Some(requester) =>
                // normal request queued
                notifyRequester(requester)
                tpaNormalRequests.remove(player)
                player.sendRichMessage(Messages.Notice.TeleportDenied)
                true
    
              case None =>
                // no normal; check for a “here” request
                tpaHereRequests.find { case (_, tgt) => tgt == player } match {
                  case Some((requester, _)) =>
                    notifyRequester(requester)
                    tpaHereRequests.remove(requester)
                    player.sendRichMessage(Messages.Notice.TeleportDenied)
                    true
    
                  case None =>
                    // truly nothing to deny
                    player.sendRichMessage(Messages.Notice.NoPendingRequest)
                    false
                }
            }
        }
    }

    private def tpaHereCommand(player: Player, target: Player): Boolean = {
        if (target == null || !target.isOnline) {
            player.sendRichMessage(Messages.Error.PlayerNotFound)
            return false
        }

        if (player == target) {
            player.sendRichMessage(Messages.Error.CannotTeleportSelfHere)
            return false
        }

        if (tpaHereRequests.get(player).exists(_ == target)) {
            player.sendRichMessage(Messages.Error.RequestAlreadyPending)
            return false
        }

        tpaHereRequests.put(player, target)

        val requestMsg =
            s"""<dark_aqua><name></dark_aqua> <gray>wants you to teleport to them.
            |<click:run_command:'/tpaccept ${player.getName}'><hover:show_text:'<gray>Click to teleport to <dark_aqua><name></dark_aqua>'><green>[Accept]</green></hover></click>
            |<click:run_command:'/tpdeny ${player.getName}'><hover:show_text:'<gray>Click to deny teleport to <dark_aqua><name></dark_aqua>'><red>[Deny]</red></hover></click>
            |""".stripMargin

        target.sendRichMessage(requestMsg, Placeholder.component("name", Component.text(player.getName, NamedTextColor.DARK_AQUA)))
        player.sendRichMessage(Messages.Notice.TpaHereRequestSent.replace("<target>", target.getName))
        true
    }

    private def backCommand(player: Player): Boolean = {
        playerLocations.get(player) match {
            case Some(location) => {
                teleportAsync(player, location, Messages.Notice.TeleportBackSuccess, "")
                playerLocations.remove(player)
                true
            }
            case None => {
                player.sendRichMessage(Messages.Notice.NoPreviousLocation)
                false
            }
        }
    }

    enum TeleportType {
        case Normal, Here
    }
}
