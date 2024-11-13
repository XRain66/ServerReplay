package me.senseiwells.replay.chunk

import me.senseiwells.replay.rejoin.RejoinConnection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl

class ChunkGamePacketPacketListener(
    private val recorder: ChunkRecorder,
    player: ServerPlayer
): ServerGamePacketListenerImpl(
    recorder.server,
    RejoinConnection(),
    player,
    CommonListenerCookie.createInitial(recorder.profile, false)
) {
    override fun send(packet: Packet<*>, listener: PacketSendListener?) {
        this.recorder.record(packet)
    }
}