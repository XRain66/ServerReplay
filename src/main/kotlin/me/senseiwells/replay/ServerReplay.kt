package me.senseiwells.replay

import me.senseiwells.replay.api.ServerReplayPluginManager
import me.senseiwells.replay.commands.PackCommand
import me.senseiwells.replay.commands.ReplayCommand
import me.senseiwells.replay.config.ReplayConfig
import me.senseiwells.replay.download.DownloadHost
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

    @JvmField
    val logger: Logger = LoggerFactory.getLogger("ServerReplay")

    val replay: ModContainer = FabricLoader.getInstance().getModContainer(MOD_ID).get()
    val version: String = this.replay.metadata.version.friendlyString

    @JvmStatic
    var config: ReplayConfig = ReplayConfig()
        private set

    override fun onInitialize() {
        this.reload()

        ServerReplayPluginManager.loadPlugins()

        ServerLifecycleEvents.SERVER_STARTING.register { this.downloads?.start() }
        ServerLifecycleEvents.SERVER_STOPPING.register { this.downloads?.stop() }

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

        if (this.config.allowDownloadingReplays) {
            val downloads = DownloadHost(this.config.replayServerIp, this.config.replayDownloadPort)
            downloads.start()
            this.downloads = downloads
        } else {
            this.downloads?.stop()
        }
    }
}