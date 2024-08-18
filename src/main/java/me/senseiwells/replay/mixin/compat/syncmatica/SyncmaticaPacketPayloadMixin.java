package me.senseiwells.replay.mixin.compat.syncmatica;

import ch.endte.syncmatica.network.SyncmaticaPacket;
import me.senseiwells.replay.api.network.RecordablePayload;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SyncmaticaPacket.Payload.class)
public class SyncmaticaPacketPayloadMixin implements RecordablePayload {
    @Override
    @SuppressWarnings("AddedMixinMembersNamePattern")
    public boolean shouldRecord() {
        return false;
    }
}
