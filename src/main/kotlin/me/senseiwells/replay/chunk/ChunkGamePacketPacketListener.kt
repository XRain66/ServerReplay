package me.senseiwells.replay.chunk

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import me.senseiwells.replay.rejoin.RejoinConnection
import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl

class ChunkGamePacketPacketListener(
    private val recorder: ChunkRecorder,
    player: ServerPlayer
): ServerGamePacketListenerImpl(
    recorder.server,
    RejoinConnection(),
    player
) {
    override fun send(packet: Packet<*>, futureListeners: GenericFutureListener<out Future<in Void>>?) {
        this.recorder.record(packet)
    }
}