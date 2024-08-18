package me.senseiwells.replay.compat.voicechat

import me.senseiwells.replay.api.network.RecordablePayload
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

class VoicechatPayload private constructor(
    private val type: CustomPacketPayload.Type<*>,
    private val writer: (FriendlyByteBuf) -> Unit
): CustomPacketPayload, RecordablePayload {
    override fun shouldRecord(): Boolean {
        return true
    }

    override fun write(buf: FriendlyByteBuf) {
        this.writer.invoke(buf)
    }

    override fun type(): CustomPacketPayload.Type<*> {
        return this.type
    }

    companion object {
        fun of(type: CustomPacketPayload.Type<*>, writer: (FriendlyByteBuf) -> Unit): VoicechatPayload {
            return VoicechatPayload(type, writer)
        }
    }
}