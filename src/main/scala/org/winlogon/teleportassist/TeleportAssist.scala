// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.plugin.java.JavaPlugin

class TeleportAssist extends JavaPlugin {
    private val isFolia: Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    } catch {
        case _: ClassNotFoundException => false
    }

    private val tpaHandler = new TpaHandler(this, isFolia)
    private val commandHandler = new CommandHandler(this, tpaHandler)
    private val playerRemover = new PlayerRemover(tpaHandler)

    override def onEnable(): Unit = {
        getLogger.info(s"TeleportAssist loaded!")
        getLogger.info(s"This server is running on ${if (isFolia) "Folia" else "Paper"}")
        
        getServer.getPluginManager.registerEvents(playerRemover, this)
    }

    override def onDisable(): Unit = {
        getLogger.info("TeleportAssist disabled!")
    }
}
