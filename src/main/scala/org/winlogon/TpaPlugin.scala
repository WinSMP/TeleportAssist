package org.winlogon

import dev.jorel.commandapi.CommandAPI
import org.bukkit.plugin.java.JavaPlugin

class TpaPlugin extends JavaPlugin {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  private val tpaHandler = new TpaHandler(this)
  var isFolia: Boolean = _

  override def onEnable(): Unit = {
    isFolia = Utilities.detectFolia()
    getLogger.info(s"TpaPlugin started!")
    getLogger.info(s"This server is running on ${if (isFolia) "Folia" else "Paper"}")

    // Register CommandAPI commands
    tpaHandler.registerCommands()
  }

  override def onDisable(): Unit = {
    getLogger.info("TpaPlugin disabled!")
  }
}
