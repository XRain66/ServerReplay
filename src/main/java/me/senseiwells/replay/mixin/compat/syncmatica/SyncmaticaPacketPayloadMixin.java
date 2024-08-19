package me.senseiwells.replay.mixin.compat.syncmatica;

import ch.endte.syncmatica.network.SyncmaticaPacket;
import me.senseiwells.replay.api.network.RecordablePayload;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SyncmaticaPacket.Payload.class)
@SuppressWarnings("AddedMixinMembersNamePattern")
public class SyncmaticaPacketPayloadMixin implements RecordablePayload {
    @Override
    public boolean shouldRecord() {
        return false;
    }

    @Override
    public void record(@NotNull FriendlyByteBuf buf) {

    }
}
