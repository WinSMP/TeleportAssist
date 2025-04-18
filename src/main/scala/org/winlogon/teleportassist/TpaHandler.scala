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

class TpaHandler(tpaPlugin: TeleportAssist) {

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
                val targetOption = Option(args.getOptional("player")).map(_.asInstanceOf[Player])
                tpAcceptCommand(player, targetOption.map(_.getName))
                ()
            }).register()

        CommandAPICommand("tpdeny")
            .withOptionalArguments(new PlayerArgument("player"))
            .executesPlayer((player: Player, args: CommandArguments) => {
                val targetOption = Option(args.getOptional("player")).map(_.asInstanceOf[Player])
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
            player.getServer.getRegionScheduler.execute(tpaPlugin, location, () => task())
        } else {
            Bukkit.getScheduler.runTask(tpaPlugin, () => task())
        }
    }

    private def teleportAsync(player: Player, location: Location, successMessage: String, notifyMessage: String): Unit = {
        if (isFolia) {
            val scheduler = player.getScheduler
            scheduler.execute(tpaPlugin, () => {
                player.teleportAsync(location).thenAccept(_ => {
                    if (successMessage.nonEmpty) player.sendRichMessage(successMessage)
                    if (notifyMessage.nonEmpty) player.sendRichMessage(notifyMessage)
                })
            }, () => {}, 0L)
        } else {
            Bukkit.getScheduler.runTaskAsynchronously(tpaPlugin, () => {
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
            s"""
            |<dark_aqua><name></dark_aqua> <gray>wants to teleport to you.
            |<click:run_command:'/tpaccept <name>'><green>[Accept]</green></click>
            |<click:run_command:'/tpdeny <name>'><red>[Deny]</red></click>
            |""".stripMargin

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
        def sendDenyMsg(requester: Player): Unit = {
            requester.sendRichMessage(
                """
                |<gray>Your teleport request to <dark_aqua><target></dark_aqua> was <red>denied</red>.
                |""".stripMargin,
                Placeholder.component("target", Component.text(player.getName, NamedTextColor.DARK_AQUA))
            )
        }
        player.sendRichMessage(Messages.Notice.TeleportDenied)

        requesterName match {
            case Some(name) => {
                val requester = Bukkit.getPlayer(name)
                if (requester == null || !requester.isOnline) {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", name))
                    return false
                }

                if (tpaNormalRequests.get(player).contains(requester)) {
                    sendDenyMsg(requester)
                    tpaNormalRequests.remove(player)
                    true
                } else if (tpaHereRequests.get(requester).contains(player)) {
                    sendDenyMsg(requester)
                    tpaHereRequests.remove(requester)
                    true
                } else {
                    player.sendRichMessage(Messages.Notice.NoPendingRequest)
                    false
                }
            }
            case None => {
                tpaNormalRequests.find(_._1 == player) match {
                    case Some((_, requester)) => {
                        sendDenyMsg(requester)
                        tpaNormalRequests.remove(player)
                        true
                    }
                    case None => {
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((requester, _)) => {
                                sendDenyMsg(requester)
                                tpaHereRequests.remove(requester)
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
            s"""
            |<dark_aqua><name></dark_aqua> <gray>wants you to teleport to them.
            |<click:run_command:'/tpaccept <name>'><green>[Accept]</green></click>
            |<click:run_command:'/tpdeny <name>'><red>[Deny]</red></click>
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
