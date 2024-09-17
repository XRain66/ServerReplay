package me.senseiwells.replay.ducks;

import me.senseiwells.replay.viewer.ReplayViewer;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ReplayViewable {
	void replay$startViewingReplay(ReplayViewer viewer);

	void replay$stopViewingReplay();

	@Nullable ReplayViewer replay$getViewingReplay();

	void replay$sendReplayViewerPacket(Packet<?> packet);
}
