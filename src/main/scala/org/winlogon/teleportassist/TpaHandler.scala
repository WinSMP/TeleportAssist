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
            Bukkit.getScheduler.runTask(tpaAssist, (() => task()): Runnable)
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
            teleportingPlayer.teleportAsync(location).thenAccept(_ => {
                if (successMessage.nonEmpty) teleportingPlayer.sendRichMessage(successMessage)
                if (notifyMessage.nonEmpty && notifyPlayer != null) {
                    notifyPlayer.sendRichMessage(notifyMessage)
                }
            })
        }
    }

    def tpaCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendRichMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (player == target) {
            player.sendRichMessage(Messages.Error.CannotTeleportSelf)
            return
        }

        if (tpaNormalRequests.get(target).contains(player)) {
            player.sendRichMessage(Messages.Error.RequestAlreadyPending)
            return
        }

        tpaNormalRequests.put(target, player)

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Normal)
        target.sendMessage(requestMsg)
        player.sendRichMessage(Messages.Notice.TpaRequestSent.replace("<target>", target.getName))
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
        // Try to resolve a (requester, teleportType) pair
        val resolved: Option[(Player, TeleportType)] = {
            requester.flatMap { r =>
                if (tpaNormalRequests.get(player).contains(r)) {
                    Some(r -> TeleportType.Normal)
                } else if (tpaHereRequests.get(r).contains(player)) {
                    Some(r -> TeleportType.Here)
                } else {
                    None
                }
            } orElse {
                // None case: remove a normal request or find a "here" request
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
                requester match {
                    case Some(r) =>
                        player.sendRichMessage(
                            Messages.Error.PlayerNotOnline.replace("<player>", r.getName)
                        )
                    case None =>
                        player.sendRichMessage(Messages.Notice.NoPendingRequest)
                }
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
                    r.sendRichMessage(s"<gray>Your teleport request to <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                    tpaNormalRequests.remove(player)
                } else if (tpaHereRequests.get(r).contains(player)) {
                    r.sendRichMessage(s"<gray>Your teleport request to summon <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                    tpaHereRequests.remove(r)
                } else {
                    player.sendRichMessage(Messages.Error.PlayerNotOnline.replace("<player>", r.getName))
                }
            case None =>
                tpaNormalRequests.remove(player) match {
                    case Some(r) =>
                        r.sendRichMessage(s"<gray>Your teleport request to <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                    case None =>
                        tpaHereRequests.find(_._2 == player) match {
                            case Some((r, _)) =>
                                r.sendRichMessage(s"<gray>Your teleport request to summon <dark_aqua>${player.getName}</dark_aqua> was <red>denied</red>")
                                tpaHereRequests.remove(r)
                            case None =>
                                player.sendRichMessage(Messages.Notice.NoPendingRequest)
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
                    requester, player.getLocation,
                    Messages.Notice.TeleportSuccess.replace("<player>", player.getName),
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

    def tpaHereCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendRichMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (player == target) {
            player.sendRichMessage(Messages.Error.CannotTeleportSelfHere)
            return
        }

        if (tpaHereRequests.get(player).contains(target)) {
            player.sendRichMessage(Messages.Error.RequestAlreadyPending)
            return
        }

        tpaHereRequests.put(player, target)

        val requestMsg = makeTeleportRequest(player, target, TeleportType.Here)
        target.sendMessage(requestMsg)
        player.sendRichMessage(Messages.Notice.TpaHereRequestSent.replace("<target>", target.getName))
    }

    def backCommand(player: Player): Unit = {
        playerLocations.get(player) match {
            case Some(location) =>
                teleportAsync(player, location, Messages.Notice.TeleportBackSuccess, null, "")
                playerLocations.remove(player)
            case None =>
                player.sendRichMessage(Messages.Notice.NoPreviousLocation)
        }
    }

    def tpaCancelCommand(player: Player, target: Player): Unit = {
        if (target == null || !target.isOnline) {
            player.sendRichMessage(Messages.Error.PlayerNotFound)
            return
        }

        if (tpaNormalRequests.get(target).contains(player)) {
            tpaNormalRequests.remove(target)
            player.sendRichMessage(Messages.Notice.TpaRequestCancelled.replace("<target>", target.getName))
            target.sendRichMessage(Messages.Notice.TpaRequestCancelledByOther.replace("<player>", player.getName))
        } else if (tpaHereRequests.get(player).contains(target)) {
            tpaHereRequests.remove(player)
            player.sendRichMessage(Messages.Notice.TpaRequestCancelled.replace("<target>", target.getName))
            target.sendRichMessage(Messages.Notice.TpaRequestCancelledByOther.replace("<player>", player.getName))
        } else {
            player.sendRichMessage(Messages.Error.NoRequestToCancel.replace("<target>", target.getName))
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

    /**
     * Extracts and formats a verb from a short command description.
     *
     * The method takes the last word of the given message, capitalizes its
     * first letter, and returns it.
     *
     * This is primarily used to derive button labels such as "Accept" or "Deny" from phrases like
     * "Click to accept" or "Click to deny".
     *
     * @param message A space-separated message whose last word represents an action verb. Must contain at least one word.
     * @return The extracted verb with its first letter capitalized.
     */
    private def makeVerb(message: String): String = {
        val vrb = message.split(" ").last
        vrb.substring(0, 1).toUpperCase() + vrb.substring(1);
    }

    /**
     * Builds an interactive teleport request message for the target player.
     *
     * The returned component shows who initiated the request, whether they want
     * the target to teleport to them or vice versa, and includes clickable
     * <b>Accept</b> and <b>Deny</b> buttons. These buttons run `/tpaccept <sender>`
     * or `/tpdeny <sender>` respectively and include hover text for clarity.
     *
     * @param sender The player who initiated the teleport request.
     * @param target The player receiving the teleport request. (Used implicitly as the message recipient.)
     * @param tpType The type of teleport request, determining the intent text (teleport here vs. teleport to target).
     * @return A compact Adventure [[Component]] representing the formatted, clickable teleport request message.
     */
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
