package org.winlogon.teleportassist

import dev.jorel.commandapi.CommandAPI
import org.bukkit.plugin.java.JavaPlugin

class TeleportAssist extends JavaPlugin {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  private val tpaHandler = new TpaHandler(this)
  var isFolia: Boolean = _

  override def onEnable(): Unit = {
    isFolia = Utilities.detectFolia()
    getLogger.info(s"TeleportAssist loaded!")
    getLogger.info(s"This server is running on ${if (isFolia) "Folia" else "Paper"}")

    tpaHandler.registerCommands()
  }

  override def onDisable(): Unit = {
    getLogger.info("TeleportAssist disabled!")
  }
}
