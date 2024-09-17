package me.senseiwells.replay.viewer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.senseiwells.replay.ducks.ReplayViewable
import me.senseiwells.replay.mixin.viewer.ClientboundPlayerInfoUpdatePacketAccessor
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry
import net.minecraft.server.network.ServerGamePacketListenerImpl
import java.util.EnumSet
import com.replaymod.replaystudio.protocol.Packet as ReplayPacket

object ReplayViewerUtils {
    fun ReplayPacket.toClientboundPlayPacket(): Packet<*> {
        useByteBuf(this.buf) { buf ->
            return CLIENTBOUND_PLAY_CODEC.createPacket(this.id, buf)
                ?: throw IllegalStateException("Failed to create play packet with id ${this.id}")
        }
    }

    private inline fun <T> useByteBuf(buf: com.github.steveice10.netty.buffer.ByteBuf, block: (FriendlyByteBuf) -> T): T {
        // When we compile we map steveice10.netty -> io.netty
        // We just need this check for dev environment
        @Suppress("USELESS_IS_CHECK")
        if (buf is ByteBuf) {
            return block(FriendlyByteBuf(buf))
        }

        val array = ByteArray(buf.readableBytes())
        buf.readBytes(array)
        val copy = FriendlyByteBuf(Unpooled.wrappedBuffer(array))
        try {
            return block(copy)
        } finally {
            copy.release()
        }
    }

    fun ServerGamePacketListenerImpl.sendReplayPacket(packet: Packet<*>) {
        (this as ReplayViewable).`replay$sendReplayViewerPacket`(packet)
    }

    fun ServerGamePacketListenerImpl.startViewingReplay(viewer: ReplayViewer) {
        (this as ReplayViewable).`replay$startViewingReplay`(viewer)
    }

    fun ServerGamePacketListenerImpl.stopViewingReplay() {
        (this as ReplayViewable).`replay$stopViewingReplay`()
    }

    fun ServerGamePacketListenerImpl.getViewingReplay(): ReplayViewer? {
        return (this as ReplayViewable).`replay$getViewingReplay`()
    }

    fun createClientboundPlayerInfoUpdatePacket(
        actions: EnumSet<Action>,
        entries: List<Entry>
    ): ClientboundPlayerInfoUpdatePacket {
        val packet = ClientboundPlayerInfoUpdatePacket(actions, listOf())
        @Suppress("KotlinConstantConditions")
        (packet as ClientboundPlayerInfoUpdatePacketAccessor).setEntries(entries)
        return packet
    }
}