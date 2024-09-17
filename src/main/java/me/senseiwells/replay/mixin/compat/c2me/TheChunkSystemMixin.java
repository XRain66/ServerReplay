package me.senseiwells.replay.mixin.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.*;
import com.ishland.flowsched.scheduler.DaemonizedStatusAdvancingScheduler;
import com.ishland.flowsched.scheduler.ItemHolder;
import com.ishland.flowsched.scheduler.ItemStatus;
import me.senseiwells.replay.chunk.ChunkRecordable;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.mixin.rejoin.ChunkMapAccessor;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadFactory;

@Mixin(value = TheChunkSystem.class, remap = false)
public abstract class TheChunkSystemMixin extends DaemonizedStatusAdvancingScheduler<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> {
    @Shadow @Final private ChunkMap tacs;

    @Shadow protected abstract ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> getUnloadedStatus();

    protected TheChunkSystemMixin(ThreadFactory threadFactory) {
        super(threadFactory);
    }

//    @ModifyExpressionValue(
//        method = "vanillaIf$setLevel",
//        at = @At(
//            value = "INVOKE",
//            target = "Lcom/ishland/c2me/rewrites/chunksystem/common/TheChunkSystem;addTicket(Ljava/lang/Object;Lcom/ishland/flowsched/scheduler/ItemTicket$TicketType;Ljava/lang/Object;Lcom/ishland/flowsched/scheduler/ItemStatus;Ljava/lang/Runnable;)Lcom/ishland/flowsched/scheduler/ItemHolder;"
//        )
//    )
//    private ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> onLoadChunk(
//        ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> original,
//        @Local(ordinal = 0) NewChunkStatus oldStatus
//    ) {
//        if (oldStatus == this.getUnloadedStatus()) {
//            ChunkHolder holder = original.getUserData().get();
//            ServerLevel level = ((ChunkMapAccessor) this.tacs).getLevel();
//            for (ChunkRecorder recorder : ChunkRecorders.containing(level.dimension(), original.getKey())) {
//                ((ChunkRecordable) holder).addRecorder(recorder);
//            }
//        }
//        return original;
//    }
//
//    @Inject(
//        method = "vanillaIf$setLevel",
//        at = @At(
//            value = "INVOKE",
//            target = "Lit/unimi/dsi/fastutil/longs/Long2IntMap;remove(J)I"
//        )
//    )
//    private void onUnloadChunk(
//        long pos,
//        int level,
//        CallbackInfoReturnable<ChunkHolder> cir,
//        @Local ChunkPos key
//    ) {
//        if (this.managedTickets.containsKey(pos)) {
//            ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> item = this.getHolder(key);
//            ((ChunkRecordable) item.getUserData().get()).removeAllRecorders();
//        }
//    }

    @Inject(
        method = "onItemUpgrade",
        at = @At("HEAD")
    )
    private void onLoadChunk(
        ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder,
        ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> statusReached,
        CallbackInfo ci
    ) {
        ServerLevel level = ((ChunkMapAccessor) this.tacs).getLevel();
        level.getServer().execute(() -> {
            for (ChunkRecorder recorder : ChunkRecorders.containing(level.dimension(), holder.getKey())) {
                ((ChunkRecordable) holder.getUserData().get()).addRecorder(recorder);
            }
        });
    }

    @Inject(
        method = "onItemDowngrade",
        at = @At("HEAD")
    )
    private void onUnloadChunk(
        ItemHolder<ChunkPos, ChunkState, ChunkLoadingContext, NewChunkHolderVanillaInterface> holder,
        ItemStatus<ChunkPos, ChunkState, ChunkLoadingContext> statusReached,
        CallbackInfo ci
    ) {
        if (((NewChunkStatus) statusReached).toChunkLevelType() == FullChunkStatus.INACCESSIBLE) {
            ServerLevel level = ((ChunkMapAccessor) this.tacs).getLevel();
            level.getServer().execute(() -> {
                ((ChunkRecordable) holder.getUserData().get()).removeAllRecorders();
            });
        }
    }
}
