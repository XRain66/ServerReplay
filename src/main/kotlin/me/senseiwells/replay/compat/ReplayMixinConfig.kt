package me.senseiwells.replay.compat

import me.senseiwells.replay.ServerReplay
import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class ReplayMixinConfig: IMixinConfigPlugin {
    companion object {
        private const val MIXIN_COMPAT = "me.senseiwells.replay.mixin.compat."
    }

    override fun onLoad(mixinPackage: String?) {

    }

    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.startsWith(MIXIN_COMPAT)) {
            val modId = mixinClassName.removePrefix(MIXIN_COMPAT).substringBefore('.')
            val isModLoaded = FabricLoader.getInstance().isModLoaded(modId)
            if (!isModLoaded) {
                ServerReplay.logger.debug("Not applying compat mixin for mod $modId, mod was not loaded")
            }
            return isModLoaded
        }
        return true
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {

    }

    override fun getMixins(): MutableList<String>? {
        return null
    }

    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {

    }

    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {

    }
}