package me.senseiwells.replay.ducks;

import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

@ApiStatus.Internal
public interface PackTracker {
	void replay$addPacks(Collection<ClientboundResourcePackPushPacket> packs);

	Collection<ClientboundResourcePackPushPacket> replay$getPacks();
}
