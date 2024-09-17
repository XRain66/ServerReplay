package me.senseiwells.replay.mixin.rejoin;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.ducks.PackTracker;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin {
	@Inject(
		method = "handleConfigurationFinished",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
			shift = At.Shift.AFTER
		)
	)
	private void afterPlayerSpawned(
		ServerboundFinishConfigurationPacket serverboundFinishConfigurationPacket,
		CallbackInfo ci,
		@Local ServerPlayer serverPlayer
	) {
		// Merge the packs into the GamePacketListener
		Collection<ClientboundResourcePackPushPacket> packs = ((PackTracker) this).replay$getPacks();
		((PackTracker) serverPlayer.connection).replay$addPacks(packs);
	}
}
