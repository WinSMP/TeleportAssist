// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

object Messages {
    object Error {
        val PlayerNotFound = "<gray>Player not found."
        val CannotTeleportSelf = "<gray>You cannot teleport to yourself."
        val RequestAlreadyPending = "<gray>You already have a pending teleport request."
        val PlayerNotOnline = "<gray>Player <dark_aqua><player></dark_aqua> is not online."
        val CannotTeleportSelfHere = "<gray>You cannot teleport yourself to yourself."
        val NoRequestToCancel = "<gray>No request to cancel for <dark_aqua><target></dark_aqua>."
    }

    object Notice {
        val TpaRequestSent = "<gray>Teleport request sent to <dark_aqua><target></dark_aqua>."
        val TpaRequestCancelled = "<gray>Teleport request to <dark_aqua><target></dark_aqua> cancelled."
        val TpaRequestCancelledByOther = "<dark_aqua><player></dark_aqua> <gray>cancelled their teleport request."
        val TpaHereRequestSent = "<gray>Teleport request sent to <dark_aqua><target></dark_aqua>."
        val NoPendingRequest = "<gray>No teleport request pending."
        val TeleportDenied = "<gray>Teleport request <red>denied</red>.</gray>"
        val TeleportSuccess = "<gray>Teleported to <dark_aqua><player></dark_aqua>."
        val TeleportHereSuccess = "<dark_aqua><player></dark_aqua> <gray>teleported to you."
        val TeleportBackSuccess = "<gray>Teleported back to previous location."
        val NoPreviousLocation = "<gray>No previous location to teleport back to."
    }
}
