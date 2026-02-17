// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext

import io.papermc.paper.command.brigadier.CommandSourceStack

import net.kyori.adventure.text.Component

import org.bukkit.entity.Player

import scala.collection.concurrent.TrieMap

import Messages.sendBuiltinMessage

class TpaHandler(
    teleportService: TeleportService,
    scheduler: Scheduler
) {
    private val tpaNormalRequests = TrieMap.empty[Player, Player]
    private val tpaHereRequests = TrieMap.empty[Player, Player]

    def tpaCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendBuiltinMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (player == target) {
            player.sendBuiltinMessage(Messages.Error.CannotTeleportSelf)
            return
        }

        if (tpaNormalRequests.get(target).contains(player)) {
            player.sendBuiltinMessage(Messages.Error.RequestAlreadyPending)
            return
        }

        tpaNormalRequests.put(target, player)

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Normal)
        target.sendMessage(requestMsg)
        player.sendBuiltinMessage(Messages.Notice.TpaRequestSent, "target" -> target.getName)
    }

    private def makeTeleportRequest(sender: Player, target: Player, tpType: TeleportType): Component = {
        val intention = if (tpType == TeleportType.Normal)
            "wants to teleport to you"
        else
            "wants you to teleport to them"
        Messages.makeTeleportRequest(sender, intention)
    }

    def tpAcceptCommand(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
        val src = ctx.getSource
        src.getExecutor match {
            case player: Player =>
                handleTpAccept(player, target)
            case _ =>
                src.getSender.sendMessage(Component.text("Only players can use this command"))
        }
        Command.SINGLE_SUCCESS
    }

    def handleTpAccept(player: Player, requester: Option[Player]): Unit = {
        val resolved: Option[(Player, TeleportType)] = {
            requester.flatMap { r =>
                if (tpaNormalRequests.get(player).contains(r)) Some(r -> TeleportType.Normal)
                else if (tpaHereRequests.get(r).contains(player)) Some(r -> TeleportType.Here)
                else None
            } orElse {
                tpaNormalRequests.remove(player).map(_ -> TeleportType.Normal)
                    .orElse(tpaHereRequests.collectFirst {
                        case (req, p) if p == player => req -> TeleportType.Here
                    })
            }
        }

        resolved match {
            case Some((r, tp)) =>
                acceptRequest(player, r, tp)
            case None =>
                player.sendBuiltinMessage(Messages.Notice.NoPendingRequest)
        }
    }

    def tpaDenyCommand(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
        val src = ctx.getSource
        src.getExecutor match {
            case player: Player =>
                handleTpDeny(player, target)
            case _ =>
                src.getSender.sendMessage(Component.text("Only players can use this command"))
        }
        Command.SINGLE_SUCCESS
    }

    def handleTpDeny(player: Player, requester: Option[Player]): Unit = {
        requester match {
            case Some(r) =>
                if (tpaNormalRequests.get(player).contains(r)) {
                    r.sendBuiltinMessage(Messages.Notice.TeleportDenied)
                    tpaNormalRequests.remove(player)
                } else if (tpaHereRequests.get(r).contains(player)) {
                    r.sendBuiltinMessage(Messages.Notice.TeleportDenied)
                    tpaHereRequests.remove(r)
                } else {
                    player.sendBuiltinMessage(Messages.Error.PlayerNotOnline, "player" -> r.getName)
                }
            case None =>
                tpaNormalRequests.remove(player) match {
                    case Some(r) =>
                        r.sendBuiltinMessage(Messages.Notice.TeleportDenied)
                    case None =>
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((r, _)) =>
                                r.sendBuiltinMessage(Messages.Notice.TeleportDenied)
                                tpaHereRequests.remove(r)
                            case None =>
                                player.sendBuiltinMessage(Messages.Notice.NoPendingRequest)
                        }
                }
        }
    }

    def acceptRequest(acceptor: Player, requester: Player, tpType: TeleportType): Unit = {
        tpType match {
            case TeleportType.Normal =>
                tpaNormalRequests.remove(acceptor)
                val dest = acceptor.getLocation
                teleportService.teleportWithWarmup(
                    requester, dest,
                    Messages.format(Messages.Notice.TeleportSuccess, "player" -> acceptor.getName),
                    acceptor,
                    Messages.format(Messages.Notice.TeleportHereSuccess, "player" -> requester.getName),
                    scheduler
                )
            case TeleportType.Here =>
                tpaHereRequests.remove(requester)
                val dest = requester.getLocation
                teleportService.teleportWithWarmup(
                    acceptor, dest,
                    Messages.format(Messages.Notice.TeleportSuccess, "player" -> requester.getName),
                    requester,
                    Messages.format(Messages.Notice.TeleportHereSuccess, "player" -> acceptor.getName),
                    scheduler
                )
        }
    }

    def tpaHereCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendBuiltinMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (player == target) {
            player.sendBuiltinMessage(Messages.Error.CannotTeleportSelfHere)
            return
        }

        if (tpaHereRequests.get(player).contains(target)) {
            player.sendBuiltinMessage(Messages.Error.RequestAlreadyPending)
            return
        }

        tpaHereRequests.put(player, target)

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Here)
        target.sendMessage(requestMsg)
        player.sendBuiltinMessage(Messages.Notice.TpaHereRequestSent, "target" -> target.getName)
    }

    def tpaCancelCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendBuiltinMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (tpaNormalRequests.get(target).contains(player)) {
            tpaNormalRequests.remove(target)
            player.sendBuiltinMessage(Messages.Notice.TpaRequestCancelled, "target" -> target.getName)
            target.sendBuiltinMessage(Messages.Notice.TpaRequestCancelledByOther, "player" -> player.getName)
        } else if (tpaHereRequests.get(player).contains(target)) {
            tpaHereRequests.remove(player)
            player.sendBuiltinMessage(Messages.Notice.TpaRequestCancelled, "target" -> target.getName)
            target.sendBuiltinMessage(Messages.Notice.TpaRequestCancelledByOther, "player" -> player.getName)
        } else {
            player.sendBuiltinMessage(Messages.Error.NoRequestToCancel, "target" -> target.getName)
        }
    }

    def removePlayer(player: Player): Unit = {
        def cleanMap(map: TrieMap[Player, Player]): Unit = {
            val keysToRemove = map.collect {
                case (k, v) if k == player || v == player => k
            }
            keysToRemove.foreach(map.remove)
        }

        cleanMap(tpaNormalRequests)
        cleanMap(tpaHereRequests)
    }

    enum TeleportType {
        case Normal, Here
    }
}
