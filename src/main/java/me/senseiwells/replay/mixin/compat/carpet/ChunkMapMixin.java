package me.senseiwells.replay.mixin.compat.carpet;

import carpet.patches.EntityPlayerMPFake;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
    @ModifyExpressionValue(
        method = "isChunkTracked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/PlayerChunkSender;isPending(J)Z"
        )
    )
    private boolean isChunkPending(boolean original, ServerPlayer player) {
        return original && !(player instanceof EntityPlayerMPFake);
    }
}
