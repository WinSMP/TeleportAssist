// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import org.bukkit.entity.Player

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType

import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.command.brigadier.{Commands, CommandSourceStack}
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class CommandHandler(val tpaAssist: TeleportAssist, handler: TpaHandler) {
    val teleportCommand = Commands.literal("tpa")
        .`then`(Commands.argument("target", ArgumentTypes.player())
            .requires(source => source.getSender.isInstanceOf[Player])
            .executes(ctx => {
                val sourceStack = ctx.getSource
                val player = sourceStack.getSender.asInstanceOf[Player]
                val targetResolver = ctx.getArgument("target", classOf[PlayerSelectorArgumentResolver])
                val target = targetResolver.resolve(sourceStack).getFirst()
                handler.tpaCommand(player, target)
                Command.SINGLE_SUCCESS
            }))
        .build()

    val acceptCommand = Commands.literal("tpaccept")
        .executes(ctx => handleTpAccept(ctx, None))
        .`then`(Commands.argument("player", ArgumentTypes.player())
            .requires(source => source.getSender.isInstanceOf[Player])
            .executes(ctx => {
                val sourceStack = ctx.getSource
                val targetResolver = ctx.getArgument("player", classOf[PlayerSelectorArgumentResolver])
                val target = targetResolver.resolve(sourceStack).getFirst()
                handler.handleTpAccept(ctx, Some(target))
            }))
        .build()

    val denyCommand = Commands.literal("tpdeny")
        .executes(ctx => handleTpDeny(ctx, None))
        .`then`(Commands.argument("player", ArgumentTypes.player())
            .requires(source => source.getSender.isInstanceOf[Player])
            .executes(ctx => {
                val sourceStack = ctx.getSource
                val targetResolver = ctx.getArgument("player", classOf[PlayerSelectorArgumentResolver])
                val target = targetResolver.resolve(sourceStack).getFirst()
                handleTpDeny(ctx, Some(target))
            }))
        .build()

    val teleportHere = Commands.literal("tpahere")
        .`then`(Commands.argument("target", ArgumentTypes.player())
            .requires(source => source.getSender.isInstanceOf[Player])
            .executes(ctx => {
                val sourceStack = ctx.getSource
                val player = sourceStack.getSender.asInstanceOf[Player]
                val targetResolver = ctx.getArgument("target", classOf[PlayerSelectorArgumentResolver])
                val target = targetResolver.resolve(sourceStack).getFirst()
                handler.tpaHereCommand(player, target)
                Command.SINGLE_SUCCESS
            }))
        .build()

    val teleportBackCommand = Commands.literal("tpback")
        .requires(source => source.getSender.isInstanceOf[Player])
        .executes(ctx => {
            val player = ctx.getSource.getSender.asInstanceOf[Player]
            handler.backCommand(player)
            Command.SINGLE_SUCCESS
        })
        .build()

    tpaAssist.getLifecycleManager().registerEventHandler(
        LifecycleEvents.COMMANDS,
        (event: ReloadableRegistrarEvent[Commands]) => {
            val registrar = event.registrar()
            registrar.register(teleportCommand, "Ask to teleport to a player")
            registrar.register(teleportHere, "Ask to teleport a player to you")
            registrar.register(acceptCommand, "Accept a player's teleport request")
            registrar.register(denyCommand, "Deny someone's teleport request")
            registrar.register(teleportBackCommand, "Go back to where you were before teleporting")
        }
    )
}
