package me.senseiwells.replay.ducks;

import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import org.jetbrains.annotations.Nullable;

public interface ServerReplay$PackTracker {
	void replay$setPack(@Nullable ClientboundResourcePackPacket pack);

	@Nullable ClientboundResourcePackPacket replay$getPack();
}
