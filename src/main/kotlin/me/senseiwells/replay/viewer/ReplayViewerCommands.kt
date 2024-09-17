package me.senseiwells.replay.viewer

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.RootCommandNode
import me.senseiwells.replay.util.DateTimeUtils.formatHHMMSS
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ChatType
import net.minecraft.commands.arguments.TimeArgument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundChatPacket
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import java.util.*
import java.util.function.Consumer
import kotlin.time.Duration.Companion.milliseconds

object ReplayViewerCommands {
    private val dispatcher = CommandDispatcher<CommandSourceStack>()

    init {
        registerReplayViewCommand()
    }

    fun sendCommandPacket(consumer: Consumer<Packet<ClientGamePacketListener>>) {
        // In vanilla, we would check whether the source has
        // access to the commands, see Commands#fillUsableCommands.
        // Here we just assume all the commands are accessible
        @Suppress("UNCHECKED_CAST")
        consumer.accept(
            ClientboundCommandsPacket(this.dispatcher.root as RootCommandNode<SharedSuggestionProvider>)
        )
    }

    fun handleCommand(command: String, viewer: ReplayViewer) {
        val player = viewer.player
        val source = player.createCommandSourceStack().withSource(ReplayViewerCommandSource(viewer))
        // TODO: Exception handling
        this.dispatcher.execute(command.substring(1), source)
    }

    private fun registerReplayViewCommand() {
        this.dispatcher.register(
            Commands.literal("replay").then(
                Commands.literal("view").then(
                    Commands.literal("close").executes(::stopViewingReplay)
                ).then(
                    Commands.literal("speed").then(
                        Commands.argument("multiplier", FloatArgumentType.floatArg(0.05F)).executes(::setViewingReplaySpeed)
                    )
                ).then(
                    Commands.literal("pause").executes { pauseViewingReplay(it, true) }
                ).then(
                    Commands.literal("unpause").executes { pauseViewingReplay(it, false) }
                ).then(
                    Commands.literal("restart").executes(::restartViewingReplay)
                ).then(
                    Commands.literal("progress").then(
                        Commands.literal("show").executes(::showReplayProgress)
                    ).then(
                        Commands.literal("hide").executes(::hideReplayProgress)
                    )
                ).then(
                    Commands.literal("jump").then(
                        Commands.literal("to").then(
                            Commands.literal("marker").then(
                                Commands.literal("unnamed").then(
                                    Commands.argument("offset", TimeArgument.time()).executes { jumpToMarker(it, name = null) }
                                ).executes { jumpToMarker(it, null, 0) }
                            ).then(
                                Commands.literal("named").then(
                                    Commands.argument("name", StringArgumentType.string()).then(
                                        Commands.argument("offset", TimeArgument.time()).executes(::jumpToMarker)
                                    ).executes { jumpToMarker(it, offset = 0) }
                                )
                            )
                        ).then(
                            Commands.literal("timestamp").then(
                                Commands.argument("time", TimeArgument.time()).executes(::jumpTo)
                            )
                        )
                    )
                ).then(
                    Commands.literal("list").then(
                        Commands.literal("markers").executes(::listMarkers)
                    )
                )
            )
        )
    }

    private fun stopViewingReplay(context: CommandContext<CommandSourceStack>): Int {
        context.source.getReplayViewer().stop()
        return Command.SINGLE_SUCCESS
    }

    private fun setViewingReplaySpeed(context: CommandContext<CommandSourceStack>): Int {
        val speed = FloatArgumentType.getFloat(context, "multiplier")
        val viewer = context.source.getReplayViewer()
        viewer.setSpeed(speed)
        context.source.sendSuccess(TextComponent("Successfully set replay speed to $speed"), false)
        return Command.SINGLE_SUCCESS
    }

    private fun pauseViewingReplay(context: CommandContext<CommandSourceStack>, paused: Boolean): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.setPaused(paused)) {
            context.source.sendSuccess(TextComponent("Successfully paused replay"), false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(TextComponent("Replay was already paused"))
        return 0
    }

    private fun restartViewingReplay(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        viewer.restart()
        context.source.sendSuccess(TextComponent("Successfully restarted replay"), false)
        return Command.SINGLE_SUCCESS
    }

    private fun showReplayProgress(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.showProgress()) {
            context.source.sendSuccess(TextComponent("Successfully showing replay progress bar"), false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(TextComponent("Progress bar was already shown"))
        return 0
    }

    private fun hideReplayProgress(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.hideProgress()) {
            context.source.sendSuccess(TextComponent("Successfully hidden replay progress bar"), false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(TextComponent("Progress bar was already hidden"))
        return 0
    }

    private fun jumpToMarker(
        context: CommandContext<CommandSourceStack>,
        name: String? = StringArgumentType.getString(context, "name"),
        offset: Int = IntegerArgumentType.getInteger(context, "offset")
    ): Int {
        val viewer = context.source.getReplayViewer()
        if (viewer.jumpToMarker(name, (offset * 50).milliseconds)) {
            context.source.sendSuccess(TextComponent("Successfully jumped to marker") , false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(TextComponent("No such marker found, or offset too large"))
        return 0
    }

    private fun jumpTo(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        val time = IntegerArgumentType.getInteger(context, "time")
        if (viewer.jumpTo((time * 50).milliseconds)) {
            context.source.sendSuccess(TextComponent("Successfully jumped to timestamp"), false)
            return Command.SINGLE_SUCCESS
        }
        context.source.sendFailure(TextComponent("Timestamp provided was outside of recording"))
        return 0
    }

    private fun listMarkers(context: CommandContext<CommandSourceStack>): Int {
        val viewer = context.source.getReplayViewer()
        val markers = viewer.getMarkers()
        if (markers.isEmpty()) {
            context.source.sendSuccess(TextComponent("Replay has no markers"), false)
            return 0
        }
        val component = TextComponent("")
        val iter = viewer.getMarkers().iterator()
        for (marker in iter) {
            val time = marker.time.milliseconds.formatHHMMSS()
            component.append(TextComponent(time).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
            component.append(": ")
            component.append(TextComponent(marker.name ?: "Unnamed").withStyle(ChatFormatting.GREEN))
            if (iter.hasNext()) {
                component.append("\n")
            }
        }
        context.source.sendSuccess(component, false)
        return Command.SINGLE_SUCCESS
    }

    private fun CommandSourceStack.getReplayViewer(): ReplayViewer {
        val player = this.playerOrException
        return player.connection.getViewingReplay()
            ?: throw IllegalStateException("Player not viewing replay managed to execute this command!?")
    }

    private class ReplayViewerCommandSource(private val viewer: ReplayViewer): CommandSource {
        override fun sendMessage(component: Component, senderUUID: UUID) {
            this.viewer.send(ClientboundChatPacket(component, ChatType.SYSTEM, senderUUID))
        }

        override fun acceptsSuccess(): Boolean {
            return true
        }

        override fun acceptsFailure(): Boolean {
            return true
        }

        override fun shouldInformAdmins(): Boolean {
            return true
        }
    }
}