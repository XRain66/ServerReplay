package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.ducks.ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.TrackedEntity.class)
public class TrackedEntityMixin implements ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$chunks = new HashSet<>();

	@Shadow @Final Entity entity;
	@Shadow @Final ServerEntity serverEntity;

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(Packet<?> packet, CallbackInfo ci) {
		for (ChunkRecorder recorder : this.replay$chunks) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "updatePlayers",
		at = @At("HEAD")
	)
	private void onUpdate(List<ServerPlayer> playersList, CallbackInfo ci) {
		ChunkPos pos = this.entity.chunkPosition();
		ResourceKey<Level> level = this.entity.getLevel().dimension();
		ChunkRecorders.updateRecordable(this, level, pos);
	}

	@Inject(
		method = "broadcastRemoved",
		at = @At("HEAD")
	)
	private void onRemoved(CallbackInfo ci) {
		this.removeAllRecorders();
	}

	@Override
	public Collection<ChunkRecorder> replay$getRecorders() {
		return this.replay$chunks;
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		if (this.replay$chunks.add(recorder)) {
			recorder.addRecordable(this);
			this.serverEntity.sendPairingData(recorder::record);

			recorder.onEntityTracked(this.entity);
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$chunks.remove(recorder)) {
			recorder.onEntityUntracked(this.entity);

			recorder.record(new ClientboundRemoveEntitiesPacket(
				this.entity.getId()
			));
			recorder.removeRecordable(this);
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(this.entity.getId());
		for (ChunkRecorder recorder : this.replay$chunks) {
			recorder.onEntityUntracked(this.entity);

			recorder.record(packet);
			recorder.removeRecordable(this);
		}
		this.replay$chunks.clear();
	}
}
