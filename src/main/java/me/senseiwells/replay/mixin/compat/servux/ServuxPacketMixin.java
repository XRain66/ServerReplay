package me.senseiwells.replay.mixin.compat.servux;

import fi.dy.masa.servux.network.packet.ServuxStructuresPacket;
import me.senseiwells.replay.api.network.RecordablePayload;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ServuxStructuresPacket.class, remap = false)
@SuppressWarnings("AddedMixinMembersNamePattern")
public class ServuxPacketMixin implements RecordablePayload {
    @Override
    public boolean shouldRecord() {
        return false;
    }

    @Override
    public void record(@NotNull FriendlyByteBuf buf) {

    }
}
