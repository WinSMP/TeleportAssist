package org.winlogon

import org.bukkit.Bukkit
import org.bukkit.command.{Command, CommandSender}
import org.bukkit.plugin.java.JavaPlugin

class TpaPlugin extends JavaPlugin {
  private val errorMessages = Messages.errorMessages
  private val messages = Messages.messages

  val tpaHandler = new TpaHandler(this)
  var isFolia: Boolean = _

  override def onEnable(): Unit = {
    isFolia = detectFolia()
    getLogger.info(s"TpaPlugin started! Running on ${if (isFolia) "Folia" else "Paper"}")

    getCommand("tpa").setExecutor(tpaHandler)
    getCommand("tpaccept").setExecutor(tpaHandler)
    getCommand("tpdeny").setExecutor(tpaHandler)
    getCommand("tpahere").setExecutor(tpaHandler)
    getCommand("back").setExecutor(tpaHandler)
  }

  override def onDisable(): Unit = {
    getLogger.info("TpaPlugin disabled!")
  }

  private def detectFolia(): Boolean = {
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
      true
    } catch {
      case _: ClassNotFoundException => false
    }
  }

}
