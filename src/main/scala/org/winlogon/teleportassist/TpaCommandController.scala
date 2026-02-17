// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext

import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.command.brigadier.{Commands, CommandSourceStack}

class TpaCommandController(plugin: TeleportAssist, tpaHandler: TpaHandler) {

    private val teleportCommand = Commands.literal("tpa")
        .`then`(Commands.argument("target", ArgumentTypes.player())
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val (player, target) = playerArgument(ctx, "target")
                tpaHandler.tpaCommand(player, target)
                Command.SINGLE_SUCCESS
            }))
        .build()

    private val acceptCommand = Commands.literal("tpaccept")
        .executes(ctx => tpaHandler.tpAcceptCommand(ctx, None))
        .`then`(Commands.argument("player", ArgumentTypes.player())
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val (_, target) = playerArgument(ctx, "player")
                tpaHandler.tpAcceptCommand(ctx, Some(target))
            }))
        .build()

    private val denyCommand = Commands.literal("tpdeny")
        .executes(ctx => tpaHandler.tpaDenyCommand(ctx, None))
        .`then`(Commands.argument("player", ArgumentTypes.player())
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val (_, target) = playerArgument(ctx, "player")
                tpaHandler.tpaDenyCommand(ctx, Some(target))
            }))
        .build()

    private val teleportHere = Commands.literal("tpahere")
        .`then`(Commands.argument("target", ArgumentTypes.player())
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val (player, target) = playerArgument(ctx, "target")
                tpaHandler.tpaHereCommand(player, target)
                Command.SINGLE_SUCCESS
            }))
        .build()

    private val cancelCommand = Commands.literal("tpcancel")
        .`then`(Commands.argument("player", ArgumentTypes.player())
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val (player, target) = playerArgument(ctx, "player")
                tpaHandler.tpaCancelCommand(player, target)
                Command.SINGLE_SUCCESS
            }))
        .build()

    plugin.getLifecycleManager.registerEventHandler(
        io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
        (event: io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent[Commands]) => {
            val r = event.registrar()
            r.register(teleportCommand, "Ask to teleport to a player")
            r.register(teleportHere, "Ask to teleport a player to you")
            r.register(acceptCommand, "Accept a player's teleport request")
            r.register(denyCommand, "Deny someone's teleport request")
            r.register(cancelCommand, "Cancel a teleport request to a player")
        }
    )

    private def playerArgument(ctx: CommandContext[CommandSourceStack], argument: String): (org.bukkit.entity.Player, org.bukkit.entity.Player) = {
        val sourceStack = ctx.getSource
        val player = sourceStack.getSender.asInstanceOf[org.bukkit.entity.Player]
        val targetResolver = ctx.getArgument(argument, classOf[PlayerSelectorArgumentResolver])
        (player, targetResolver.resolve(sourceStack).getFirst)
    }
}
