package me.senseiwells.replay.mixin.network;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.codec.IdDispatchCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IdDispatchCodec.class)
public interface IdDispatchCodecAccessor<T> {
    @Accessor("toId")
    Object2IntMap<T> getTypeToIdMap();
}
