// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.entity.Player

class SpawnCommandController(plugin: TeleportAssist, spawnHandler: SpawnHandler) {

    private val spawnCommand = Commands.literal("spawn")
        .requires(src => src.getSender.isInstanceOf[Player])
        .executes(ctx => {
            spawnHandler.teleportToSpawn(ctx.getSource.getSender.asInstanceOf[Player])
            Command.SINGLE_SUCCESS
        })
        .build()

    private val setSpawnCommand = Commands.literal("setspawn")
        .requires(src => src.getSender.hasPermission("teleportassist.spawn.admin"))
        .requires(src => src.getSender.isInstanceOf[Player])
        .executes(ctx => {
            spawnHandler.setSpawn(ctx.getSource.getSender.asInstanceOf[Player])
            Command.SINGLE_SUCCESS
        })
        .build()

    plugin.getLifecycleManager.registerEventHandler(
        LifecycleEvents.COMMANDS,
        (event: ReloadableRegistrarEvent[Commands]) => {
            val r = event.registrar()
            r.register(spawnCommand, "Teleport to world spawn")
            r.register(setSpawnCommand, "Set the spawn location for this world")
        }
    )
}
