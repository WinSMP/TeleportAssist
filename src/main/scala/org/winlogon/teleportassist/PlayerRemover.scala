// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.PlayerQuitEvent

class PlayerRemover(handler: TpaHandler) extends Listener {
  @EventHandler
  def onPlayerQuit(event: PlayerQuitEvent): Unit = {
    val player = event.getPlayer
    val reason = event.getReason
    
    reason match {
      case PlayerQuitEvent.QuitReason.DISCONNECTED | PlayerQuitEvent.QuitReason.KICKED => 
        handler.removePlayer(player)
      case _ => // not player's intention to leave
    }
  }
}
