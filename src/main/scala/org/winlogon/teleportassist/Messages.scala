// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

import org.bukkit.entity.Player

object Messages {
    private val mm = MiniMessage.miniMessage()

    def format(template: String, placeholders: (String, String)*): Component =
        mm.deserialize(template, placeholders.map((k, v) => Placeholder.unparsed(k, v)): _*)

    extension (player: Player)
        def sendBuiltinMessage(template: String, placeholders: (String, String)*): Unit =
            player.sendMessage(format(template, placeholders: _*))

    object Error {
        val PlayerNotFound = "<gray>Player not found."
        val CannotTeleportSelf = "<gray>You cannot teleport to yourself."
        val RequestAlreadyPending = "<gray>You already have a pending teleport request."
        val PlayerNotOnline = "<gray>Player <dark_aqua><player></dark_aqua> is not online."
        val CannotTeleportSelfHere = "<gray>You cannot teleport yourself to yourself."
        val NoRequestToCancel = "<gray>No request to cancel for <dark_aqua><target></dark_aqua>."
        val WarpNotFound = "<red>Warp not found: <name></red>"
        val WarpAlreadyExists = "<red>Warp <name> already exists.</red>"
        val NoPermission = "<red>You don't have permission to do that.</red>"
        val InvalidWorld = "<red>Invalid world for warp.</red>"
        val DatabaseError = "<red>Database error.</red>"
        val WarmupMoving = "<red>Teleport cancelled \u2014 you moved.</red>"
        val WarmupDamage = "<red>Teleport cancelled \u2014 you took damage.</red>"
    }

    object Notice {
        val TpaRequestSent = "<gray>Teleport request sent to <dark_aqua><target></dark_aqua>."
        val TpaRequestCancelled = "<gray>Teleport request to <dark_aqua><target></dark_aqua> cancelled."
        val TpaRequestCancelledByOther = "<dark_aqua><player></dark_aqua> <gray>cancelled their teleport request."
        val TpaHereRequestSent = "<gray>Teleport request sent to <dark_aqua><target></dark_aqua>."
        val NoPendingRequest = "<gray>No teleport request pending."
        val TeleportDenied = "<gray>Teleport request <red>denied</red>."
        val TeleportSuccess = "<gray>Teleported to <dark_aqua><player></dark_aqua>."
        val TeleportHereSuccess = "<dark_aqua><player></dark_aqua> <gray>teleported to you."
        val TeleportBackSuccess = "<gray>Teleported back to previous location."
        val NoPreviousLocation = "<gray>No previous location to teleport back to."
        val WarpCreated = "<gray>Warp <name> <green>created</green>."
        val WarpRemoved = "<gray>Warp <name> <red>removed</red>."
        val WarpUpdated = "<gray>Warp <name> <dark_aqua>updated</dark_aqua>."
        val WarpTeleported = "<gray>Teleported to warp <name>."
        val NoWarps = "<gray>No warps found."
        val SpawnSet = "<gray>Spawn set for world <name>."
        val SpawnTeleported = "<gray>Teleported to spawn."
        val NoSpawnSet = "<gray>No spawn set for this world."
        val WarmupStarted = "<gray>Teleporting in <seconds>s \u2014 stand still."
    }

    /**
     * Creates an interactive teleport request message for a player.
     *
     * The returned component displays the sender's name, the supplied intention
     * message, and clickable **Accept** and **Deny** buttons.
     *
     * Clicking either button executes the corresponding `/tpaccept` or `/tpdeny`
     * command for the sender, while hovering over the buttons shows a short description.
     *
     * Example output:
     * {{{
     * Steve wants to teleport to you.
     * [Accept] [Deny]
     * }}}
     *
     * @param sender
     *   The player who initiated the teleport request. This is assumed to be properly
     *   formatted.
     * @param intention
     *   A human-readable description of the request, such as
     *   `"wants to teleport to you."` or `"wants you to teleport to them."`
     * @return
     *   A {@link Component} containing the formatted, interactive teleport
     *   request message
     */
    def makeTeleportRequest(sender: Player, intention: String): Component = {
        val acceptCmd = (s"/tpaccept ${sender.getName}", "Click to accept")
        val denyCmd = (s"/tpdeny ${sender.getName}", "Click to deny")
        val name = Placeholder.component("name", Component.text(sender.getName, NamedTextColor.DARK_AQUA))

        def makeVerb(message: String): String = {
            val vrb = message.split(" ").last
            vrb.substring(0, 1).toUpperCase() + vrb.substring(1)
        }

        val template =
            s"<dark_aqua><name></dark_aqua> <gray>$intention\n" +
            s"[<click:run_command:'${acceptCmd._1}'>" +
            s"<hover:show_text:'<gray>${acceptCmd._2}'>" +
            s"<green>${makeVerb(acceptCmd._2)}</green>" +
            s"</hover></click>] " +
            s"[<click:run_command:'${denyCmd._1}'>" +
            s"<hover:show_text:'<gray>${denyCmd._2}'>" +
            s"<red>${makeVerb(denyCmd._2)}</red>" +
            s"</hover></click>]"

        mm.deserialize(template, StandardTags.defaults(), name).compact()
    }
}
