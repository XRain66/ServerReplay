package me.senseiwells.replay.viewer

import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path
import java.util.*

object ReplayViewers {
    private val viewers = LinkedHashMap<UUID, ReplayViewer>()

    @JvmStatic
    fun start(path: Path, player: ServerPlayer): ReplayViewer {
        val viewer = ReplayViewer(path, player.connection)
        this.viewers[player.uuid] = viewer
        viewer.start()
        return viewer
    }

    @JvmStatic
    fun remove(uuid: UUID): ReplayViewer? {
        return this.viewers.remove(uuid)
    }

    @JvmStatic
    fun viewers(): Collection<ReplayViewer> {
        return ArrayList(this.viewers.values)
    }
}