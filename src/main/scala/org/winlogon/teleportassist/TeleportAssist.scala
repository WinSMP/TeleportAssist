// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.plugin.java.JavaPlugin

import java.util.logging.Logger

class TeleportAssist extends JavaPlugin {
    private lazy val isFolia: Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    } catch {
        case _: ClassNotFoundException => false
    }

    // keeps track of the players
    private var tpaHandler: TpaHandler = _
    // registers commands when this class gets instantiated
    private var commandHandler: CommandHandler = _
    // removes players when they leave or disconnect
    private var playerRemover: PlayerRemover = _
    private var logger: Logger = _

    override def onLoad(): Unit = {
        tpaHandler = TpaHandler(this, isFolia)
        commandHandler = CommandHandler(this, tpaHandler)
        playerRemover = PlayerRemover(tpaHandler)
        logger = getLogger
    }

    override def onEnable(): Unit = {
        logger.info("TeleportAssist loaded!")

        val mcVersion = getServer.getMinecraftVersion
        logger.info(s"This server is running on ${if (isFolia) "Folia" else "Paper"} $mcVersion")

        getServer.getPluginManager.registerEvents(playerRemover, this)
    }

    override def onDisable(): Unit = {
        logger.info("TeleportAssist disabled!")
    }
}
