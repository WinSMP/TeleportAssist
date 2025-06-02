// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext

import io.papermc.paper.command.brigadier.CommandSourceStack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.{Bukkit, Location}

import scala.collection.concurrent.TrieMap
import scala.jdk.OptionConverters._

class TpaHandler(val tpaAssist: TeleportAssist, val isFolia: Boolean) {
    private val tpaNormalRequests = TrieMap.empty[Player, Player]
    private val tpaHereRequests = TrieMap.empty[Player, Player]
    private val playerLocations = TrieMap.empty[Player, Location]
    private val mm = MiniMessage.miniMessage()

    private def executeTaskAsync(player: Player, location: Location, task: () => Unit): Unit = {
        if (isFolia) {
            player.getServer.getRegionScheduler.execute(tpaAssist, location, () => task())
        } else {
            Bukkit.getScheduler.runTask(tpaAssist, () => task())
        }
    }

    /**
      * Asynchronously teleports a player to a specified location with message handling.
      * This method checks whether the server is running on Folia
      *
      * @param teleportingPlayer The player being teleported (non-null)
      * @param location          The destination location (non-null)
      * @param successMessage    Message sent to teleporting player upon success (null/empty for none)
      * @param notifyPlayer      Player who should receive notification (null for none)
      * @param notifyMessage     Notification message (null/empty for none)
      */
    private def teleportAsync(
        teleportingPlayer: Player, 
        location: Location, 
        successMessage: String, 
        notifyPlayer: Player,
        notifyMessage: String
    ): Unit = {
        if (isFolia) {
            val scheduler = teleportingPlayer.getScheduler
            scheduler.execute(tpaAssist, () => {
                teleportingPlayer.teleportAsync(location).thenAccept(_ => {
                    if (successMessage.nonEmpty) teleportingPlayer.sendRichMessage(successMessage)
                    if (notifyMessage.nonEmpty && notifyPlayer != null) {
                        notifyPlayer.sendRichMessage(notifyMessage)
                    }
                })
            }, () => {}, 0L)
        } else {
            Bukkit.getScheduler.runTaskAsynchronously(tpaAssist, () => {
                teleportingPlayer.teleport(location)
                if (successMessage.nonEmpty) teleportingPlayer.sendRichMessage(successMessage)
                if (notifyMessage.nonEmpty && notifyPlayer != null) {
                    notifyPlayer.sendRichMessage(notifyMessage)
                }
            })
        }
    }

    def tpaCommand(player: Player, target: Player): Boolean = {
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

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Normal)
        target.sendMessage(requestMsg)
        player.sendRichMessage(Messages.Notice.TpaRequestSent.replace("<target>", target.getName))
        true
    }

    def handleTpAccept(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
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

    def handleTpDeny(ctx: CommandContext[CommandSourceStack], target: Option[Player]): Int = {
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

    def tpAcceptCommand(player: Player, requester: Option[Player]): Boolean = {
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

    def acceptRequest(player: Player, requester: Player, tpType: TeleportType): Unit = {
        playerLocations.put(requester, requester.getLocation)

        tpType match {
            case TeleportType.Normal =>
                tpaNormalRequests.remove(player)
                teleportAsync(
                    requester,
                    player.getLocation, Messages.Notice.TeleportSuccess.replace("<player>", player.getName),
                    player, Messages.Notice.TeleportHereSuccess.replace("<player>", requester.getName)
                )
            case TeleportType.Here =>
                tpaHereRequests.remove(requester)
                teleportAsync(
                    player, requester.getLocation,
                    Messages.Notice.TeleportSuccess.replace("<player>", requester.getName),
                    requester, Messages.Notice.TeleportHereSuccess.replace("<player>", player.getName)
                )
        }
    }

    def tpaDenyCommand(player: Player, requester: Option[Player]): Boolean = {
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

    def tpaHereCommand(player: Player, target: Player): Boolean = {
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

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Here)
        target.sendMessage(requestMsg)
        player.sendRichMessage(Messages.Notice.TpaHereRequestSent.replace("<target>", target.getName))
        true
    }

    def backCommand(player: Player): Boolean = {
        playerLocations.get(player) match {
            case Some(location) =>
                teleportAsync(player, location, Messages.Notice.TeleportBackSuccess, null, "")
                playerLocations.remove(player)
                true
            case None =>
                player.sendRichMessage(Messages.Notice.NoPreviousLocation)
                false
        }
    }

    def removePlayer(player: Player): Unit = {
        // remove all entries from the map where the player is present in any way
        def cleanMap(map: TrieMap[Player, Player]): Unit = {
            val keysToRemove = map.collect {
                case (k, v) if k == player || v == player => k
            }
            keysToRemove.foreach(map.remove)
        }
    
        cleanMap(tpaNormalRequests)
        cleanMap(tpaHereRequests)
        playerLocations.remove(player)
    }

    private def makeVerb(message: String): String = {
        val vrb = message.split(" ").last
        vrb.substring(0, 1).toUpperCase() + vrb.substring(1);
    }

    private def makeTeleportRequest(
        sender: Player,
        target: Player,
        tpType: TeleportType
    ): Component = {
        val acceptCmd = (s"/tpaccept ${sender.getName}", "Click to accept")
        val denyCmd = (s"/tpdeny ${sender.getName}", "Click to deny")
        val intention = tpType match {
          case TeleportType.Here   => "wants you to teleport to them"
          case TeleportType.Normal => "wants to teleport to you"
        }
        val name = Placeholder.component("name", Component.text(sender.getName, NamedTextColor.DARK_AQUA))

        val template =
            // first button - accept
            s"<dark_aqua><name></dark_aqua> <gray>$intention\n" +
            s"[<click:run_command:'${acceptCmd._1}'>" +
            s"<hover:show_text:'<gray>${acceptCmd._2}'>" +
            s"<green>${makeVerb(acceptCmd._2)}</green>" +
            s"</hover></click>] " + // a space so the two buttons aren't jammed together
            // second button - deny
            s"[<click:run_command:'${denyCmd._1}'>" +
            s"<hover:show_text:'<gray>${denyCmd._2}'>" +
            s"<red>${makeVerb(denyCmd._2)}</red>" +
            s"</hover></click>]"

        mm.deserialize(template, StandardTags.defaults(), name).compact()
    }

    enum TeleportType {
        case Normal, Here
    }
}
