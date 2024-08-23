package me.senseiwells.replay.mixin.compat.syncmatica;

import ch.endte.syncmatica.network.SyncmaticaPayload;
import me.senseiwells.replay.api.network.RecordablePayload;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SyncmaticaPayload.class)
@SuppressWarnings("AddedMixinMembersNamePattern")
public class SyncmaticaPacketPayloadMixin implements RecordablePayload {
    @Override
    public boolean shouldRecord() {
        return false;
    }
}
