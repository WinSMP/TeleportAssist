// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

object Messages {
    object Error {
        val PlayerNotFound = "<gray>Player not found."
        val CannotTeleportSelf = "<gray>You cannot teleport to yourself."
        val RequestAlreadyPending = "<gray>You already have a pending teleport request."
        val PlayerNotOnline = "<gray>Player <aqua><player></aqua> is not online."
        val CannotTeleportSelfHere = "<gray>You cannot teleport yourself to yourself."
    }

    object Notice {
        val TpaRequestSent = "<gray>Teleport request sent to <aqua><target></aqua>."
        val TpaHereRequestSent = "<gray>Teleport request sent to <aqua><target></aqua>."
        val NoPendingRequest = "<gray>No teleport request pending."
        val TeleportDenied = "<gray>Teleport request <red>denied</red>."
        val TeleportSuccess = "<gray>Teleported to <aqua><player></aqua>."
        val TeleportHereSuccess = "<aqua><player></aqua> <gray>teleported to you."
        val TeleportBackSuccess = "<gray>Teleported back to previous location."
        val NoPreviousLocation = "<gray>No previous location to teleport back to."
    }
}
