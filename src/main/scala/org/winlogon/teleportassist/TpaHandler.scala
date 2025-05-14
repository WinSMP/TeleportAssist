// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType

import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.command.brigadier.{Commands, CommandSourceStack}
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Location}

import scala.collection.concurrent.TrieMap
import scala.jdk.OptionConverters._

class TpaHandler(tpaAssist: TeleportAssist, isFolia: Boolean) {
    private val tpaNormalRequests = TrieMap.empty[Player, Player]
    private val tpaHereRequests = TrieMap.empty[Player, Player]
    private val playerLocations = TrieMap.empty[Player, Location]

    def registerCommands(): Unit = {
        val teleportCommand = Commands.literal("tpa")
            .`then`(Commands.argument("target", ArgumentTypes.player())
                .executes(ctx => {
                    val source = ctx.getSource
                    source.getSender match {
                        case player: Player =>
                            val targetResolver = ctx.getArgument("target", classOf[PlayerSelectorArgumentResolver])
                            val target = targetResolver.resolve(source).getFirst()
                            tpaCommand(player, target)
                        case _ =>
                            source.getSender.sendMessage(Component.text("Only players can use this command"))
                    }
                    Command.SINGLE_SUCCESS
                }))
            .build()

        val acceptCommand = Commands.literal("tpaccept")
            .executes(ctx => handleTpAccept(ctx, None))
            .`then`(Commands.argument("player", StringArgumentType.word())
                .executes(ctx => {
                    val playerName = StringArgumentType.getString(ctx, "player")
                    handleTpAccept(ctx, Option(Bukkit.getPlayer(playerName)))
                }))
            .build()

        val denyCommand = Commands.literal("tpdeny")
            .executes(ctx => handleTpDeny(ctx, None))
            .`then`(Commands.argument("player", StringArgumentType.word())
                .executes(ctx => {
                    val playerName = StringArgumentType.getString(ctx, "player")
                    handleTpDeny(ctx, Option(Bukkit.getPlayer(playerName)))
                }))
            .build()

        val teleportHere = Commands.literal("tpahere")
            .`then`(Commands.argument("target", ArgumentTypes.player())
                .requires(sender => /* ... */ true)
                .executes(ctx => {
                    val source = ctx.getSource
                    source.getSender match {
                        case player: Player =>
                            val targetResolver = ctx.getArgument("target", classOf[PlayerSelectorArgumentResolver])
                            val target = targetResolver.resolve(source).getFirst()
                            tpaHereCommand(player, target)
                        case _ =>
                            source.getExecutor.sendMessage(Component.text("Only players can use this command"))
                    }
                    Command.SINGLE_SUCCESS
                }))
            .build()

        // /back command
        val teleportBackCommand = Commands.literal("tpback")
            .requires(sender => sender.getExecutor().isInstanceOf[Player] )
            .executes(ctx => {
                ctx.getSource.getExecutor match {
                    case player: Player => backCommand(player)
                    case _ => ctx.getSource.getSender.sendMessage(Component.text("Only players can use this command"))
                }
                Command.SINGLE_SUCCESS
            })
            .build()

        tpaAssist.getLifecycleManager().registerEventHandler(
            LifecycleEvents.COMMANDS,
            (event: ReloadableRegistrarEvent[Commands]) => {
                val registrar = event.registrar()
                registrar.register(teleportCommand, "Ask to teleport to a player")
                registrar.register(acceptCommand, "Ask to teleport a player to you")
                registrar.register(denyCommand, "Accept a player's teleport request")
                registrar.register(teleportHere, "Deny someone's teleport request")
                registrar.register(teleportBackCommand, "Go back to where you were before teleporting")
            }
        )
    }

    // Helper methods remain the same as original implementation
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

    private def handleTpAccept(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
        val src = ctx.getSource
        src.getExecutor match {
            case player: Player =>
                if (tpAcceptCommand(player, target)) Command.SINGLE_SUCCESS
                else 0
            case _ =>
                src.getSender.sendMessage(Component.text("Only players can use this command"))
                Command.SINGLE_SUCCESS
        }
    }

    private def handleTpDeny(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
        val src = ctx.getSource
        src.getExecutor match {
            case player: Player =>
                if (tpaDenyCommand(player, target)) Command.SINGLE_SUCCESS
                else 0
            case _ =>
                src.getSender.sendMessage(Component.text("Only players can use this command"))
                Command.SINGLE_SUCCESS
        }
    }

    private def tpAcceptCommand(player: Player, requester: Option[Player]): Boolean = {
        requester match {
            case Some(r) =>
                if (tpaNormalRequests.get(player).contains(r)) {
                    acceptRequest(player, r, TeleportType.Normal)
                    true
                } else if (tpaHereRequests.get(r).contains(player)) {
                    acceptRequest(player, r, TeleportType.Here)
                    true
                } else {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", r.getName))
                    false
                }
            case None =>
                tpaNormalRequests.remove(player) match {
                    case Some(r) =>
                        acceptRequest(player, r, TeleportType.Normal)
                        true
                    case None =>
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((r, _)) =>
                                acceptRequest(player, r, TeleportType.Here)
                                true
                            case None =>
                                player.sendRichMessage(Messages.Notice.NoPendingRequest)
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

    private def tpaDenyCommand(player: Player, requester: Option[Player]): Boolean = {
        requester match {
            case Some(r) =>
                if (tpaNormalRequests.get(player).exists(_ == r)) {
                    r.sendRichMessage(s"<gray>Your teleport request to <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                    tpaNormalRequests.remove(player)
                    true
                } else if (tpaHereRequests.get(r).contains(player)) {
                    r.sendRichMessage(s"<gray>Your teleport request to summon <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                    tpaHereRequests.remove(r)
                    true
                } else {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", r.getName))
                    false
                }
            case None =>
                tpaNormalRequests.remove(player) match {
                    case Some(r) =>
                        r.sendRichMessage(s"<gray>Your teleport request to <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                        true
                    case None =>
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((r, _)) =>
                                r.sendRichMessage(s"<gray>Your teleport request to summon <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                                tpaHereRequests.remove(r)
                                true
                            case None =>
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
            |<click:run_command:'/tpdeny ${player.getName}'><hover:show_text:'<gray>Click to deny teleport to <dark_aqua><name></dark_aqua>'><red>[Deny]</red></hover></click>""".stripMargin

        target.sendRichMessage(requestMsg, Placeholder.component("name", Component.text(player.getName, NamedTextColor.DARK_AQUA)))
        player.sendRichMessage(Messages.Notice.TpaHereRequestSent.replace("<target>", target.getName))
        true
    }

    private def backCommand(player: Player): Boolean = {
        playerLocations.get(player) match {
            case Some(location) =>
                teleportAsync(player, location, Messages.Notice.TeleportBackSuccess, "")
                playerLocations.remove(player)
                true
            case None =>
                player.sendRichMessage(Messages.Notice.NoPreviousLocation)
                false
        }
    }

    private enum TeleportType {
        case Normal, Here
    }
}
