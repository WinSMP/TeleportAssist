// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType

import io.papermc.paper.command.brigadier.{Commands, CommandSourceStack}

class WarpCommandController(plugin: TeleportAssist, warpHandler: WarpHandler) {

    private val warpNameArg = Commands.argument("name", StringArgumentType.word())

    private val warpCreate = Commands.literal("create")
        .`then`(warpNameArg
            .requires(src => src.getSender.hasPermission("teleportassist.warp.admin"))
            .executes(ctx => {
                ctx.getSource.getSender match {
                    case player: org.bukkit.entity.Player =>
                        warpHandler.createWarp(player, StringArgumentType.getString(ctx, "name"))
                    case _ =>
                }
                Command.SINGLE_SUCCESS
            }))

    private val warpRemove = Commands.literal("remove")
        .`then`(warpNameArg
            .requires(src => src.getSender.hasPermission("teleportassist.warp.admin"))
            .executes(ctx => {
                ctx.getSource.getSender match {
                    case player: org.bukkit.entity.Player =>
                        warpHandler.removeWarp(player, StringArgumentType.getString(ctx, "name"))
                    case _ =>
                }
                Command.SINGLE_SUCCESS
            }))

    private val warpEdit = Commands.literal("edit")
        .`then`(warpNameArg
            .requires(src => src.getSender.hasPermission("teleportassist.warp.admin"))
            .executes(ctx => {
                ctx.getSource.getSender match {
                    case player: org.bukkit.entity.Player =>
                        warpHandler.editWarp(player, StringArgumentType.getString(ctx, "name"))
                    case _ =>
                }
                Command.SINGLE_SUCCESS
            }))

    private val warpTp = Commands.literal("tp")
        .`then`(warpNameArg
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val player = ctx.getSource.getSender.asInstanceOf[org.bukkit.entity.Player]
                warpHandler.teleportToWarp(player, StringArgumentType.getString(ctx, "name"))
                Command.SINGLE_SUCCESS
            }))

    private val warpTeleport = Commands.literal("teleport")
        .`then`(warpNameArg
            .requires(src => src.getSender.isInstanceOf[org.bukkit.entity.Player])
            .executes(ctx => {
                val player = ctx.getSource.getSender.asInstanceOf[org.bukkit.entity.Player]
                warpHandler.teleportToWarp(player, StringArgumentType.getString(ctx, "name"))
                Command.SINGLE_SUCCESS
            }))

    private val warpList = Commands.literal("list")
        .executes(ctx => {
            ctx.getSource.getSender match {
                case player: org.bukkit.entity.Player => warpHandler.listWarps(player)
                case _ =>
            }
            Command.SINGLE_SUCCESS
        })

    private val warpCommand = Commands.literal("warp")
        .executes(ctx => {
            ctx.getSource.getSender match {
                case player: org.bukkit.entity.Player =>
                    player.sendRichMessage(
                        "<gray>Usage: /warp <create|remove|edit|tp|teleport|list> [name]"
                    )
                case _ =>
            }
            Command.SINGLE_SUCCESS
        })
        .`then`(warpCreate)
        .`then`(warpRemove)
        .`then`(warpEdit)
        .`then`(warpTp)
        .`then`(warpTeleport)
        .`then`(warpList)
        .build()

    plugin.getLifecycleManager.registerEventHandler(
        io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
        (event: io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent[Commands]) => {
            event.registrar().register(warpCommand, "Create, edit, remove, list, or teleport to warps")
        }
    )
}
