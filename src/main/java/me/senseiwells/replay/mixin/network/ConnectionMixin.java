package me.senseiwells.replay.mixin.network;

import com.llamalad7.mixinextras.sugar.Local;
import io.netty.channel.ChannelPipeline;
import me.senseiwells.replay.http.DownloadPacksHttpInjector;
import me.senseiwells.replay.http.DownloadReplaysHttpInjector;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(
        method = "configureSerialization",
        at = @At("TAIL")
    )
    private static void onConfigureSerialization(CallbackInfo ci, @Local(argsOnly = true) ChannelPipeline pipeline) {
        pipeline.addFirst(DownloadPacksHttpInjector.INSTANCE);
        pipeline.addFirst(DownloadReplaysHttpInjector.INSTANCE);
    }
}
