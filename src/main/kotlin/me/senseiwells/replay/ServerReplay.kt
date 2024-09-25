package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.download.DownloadHost
import net.casual.arcade.host.PackHost
import net.casual.arcade.host.pack.ReadablePack
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ServerReplay: ModInitializer {
    const val MOD_ID = "server-replay"

    private var downloads: DownloadHost? = null
    private var packs: PackHost? = null

    @JvmField
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val replay: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = this.replay.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig()
        private set

    override fun onInitialize() {
        this.config = ReplayConfig.read()

        ServerReplayPluginManager.loadPlugins()

        ServerLifecycleEvents.SERVER_STARTING.register {
            this.reloadHost()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            this.downloads?.stop()
            this.packs?.stop()
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ReplayCommand.register(dispatcher)

            if (this.config.debug) {
                PackCommand.register(dispatcher)
            }
        }
    }

    fun getDownloadUrl(): String? {
        return this.downloads?.getUrl()
    }

    fun reload() {
        this.config = ReplayConfig.read()

        this.reloadHost()
    }

    fun hostPack(pack: ReadablePack): PackHost.HostedPackRef? {
        return this.packs?.addPack(pack)
    }

    fun removePack(pack: ReadablePack) {
        this.packs?.removePack(pack.name)
    }

    private fun reloadHost() {
        this.downloads?.stop()
        this.packs?.stop()

        if (this.config.allowDownloadingReplays) {
            val downloads = DownloadHost(this.config.replayServerIp, this.config.replayDownloadPort)
            downloads.start()
            this.downloads = downloads
        }
        val packs = PackHost(this.config.replayServerIp, this.config.replayViewerPackPort)
        packs.start()
        this.packs = packs
    }
}