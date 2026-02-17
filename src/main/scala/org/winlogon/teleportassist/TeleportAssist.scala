// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.asynccraftr.AsyncCraftr

import java.util.logging.Logger

class TeleportAssist extends JavaPlugin {
    private var database: Database = _
    private var teleportService: TeleportService = _
    private var scheduler: Scheduler = _
    private var tpaHandler: TpaHandler = _
    private var warpHandler: WarpHandler = _
    private var spawnHandler: SpawnHandler = _
    private var playerListener: PlayerListener = _
    private var logger: Logger = _

    override def onLoad(): Unit = {
        logger = getLogger
    }

    override def onEnable(): Unit = {
        saveDefaultConfig()
        val warmupSeconds = getConfig.getInt("warmup-seconds", 3)
        scheduler = Scheduler(this, warmupSeconds)
        database = Database(getDataFolder)
        database.init()
        logger.info("Database initialized")

        teleportService = TeleportService(this)
        tpaHandler = TpaHandler(teleportService, scheduler)
        warpHandler = WarpHandler(this, database, teleportService, scheduler)
        spawnHandler = SpawnHandler(this, database, teleportService, scheduler)

        TpaCommandController(this, tpaHandler)
        WarpCommandController(this, warpHandler)
        SpawnCommandController(this, spawnHandler)
        BackCommandController(this, teleportService, database, scheduler)

        playerListener = PlayerListener(tpaHandler, database, scheduler)
        getServer.getPluginManager.registerEvents(playerListener, this)

        val mcVersion = getServer.getMinecraftVersion
        val isFolia = AsyncCraftr.isRunningOnFolia
        logger.info(s"TeleportAssist loaded! Running on ${if (isFolia) "Folia" else "Paper"} $mcVersion")
    }

    override def onDisable(): Unit = {
        if (database != null) database.close()
        logger.info("TeleportAssist disabled!")
    }
}
