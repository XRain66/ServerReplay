package me.senseiwells.replay.mixin.compat.syncmatica;

import ch.endte.syncmatica.network.SyncmaticaPayload;
import me.senseiwells.replay.api.network.RecordablePayload;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SyncmaticaPayload.class, remap = false)
@SuppressWarnings("AddedMixinMembersNamePattern")
public class SyncmaticaPacketPayloadMixin implements RecordablePayload {
    @Override
    public boolean shouldRecord() {
        return false;
    }
}
