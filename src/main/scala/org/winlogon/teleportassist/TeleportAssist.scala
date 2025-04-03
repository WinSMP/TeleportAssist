package org.winlogon.teleportassist

import dev.jorel.commandapi.CommandAPI
import org.bukkit.plugin.java.JavaPlugin

class TeleportAssist extends JavaPlugin {
  private var isFolia: Boolean = _

  private val tpaHandler = new TpaHandler(this)

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
