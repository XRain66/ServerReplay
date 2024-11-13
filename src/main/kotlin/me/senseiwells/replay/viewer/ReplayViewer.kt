package me.senseiwells.replay.viewer

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.TreeMultimap
import com.replaymod.replaystudio.PacketData
import com.replaymod.replaystudio.data.Marker
import com.replaymod.replaystudio.io.ReplayInputStream
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion
import com.replaymod.replaystudio.protocol.PacketTypeRegistry
import com.replaymod.replaystudio.replay.ZipReplayFile
import com.replaymod.replaystudio.studio.ReplayStudio
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.ducks.PackTracker
import me.senseiwells.replay.http.DownloadPacksHttpInjector
import me.senseiwells.replay.mixin.viewer.EntityInvoker
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer
import me.senseiwells.replay.util.MathUtils
import me.senseiwells.replay.viewer.ReplayViewerUtils.getViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.sendReplayPacket
import me.senseiwells.replay.viewer.ReplayViewerUtils.startViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.stopViewingReplay
import me.senseiwells.replay.viewer.ReplayViewerUtils.toClientboundPlayPacket
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.BossEvent.BossBarColor
import net.minecraft.world.BossEvent.BossBarOverlay
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import net.minecraft.world.level.biome.BiomeManager
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.function.Supplier
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ReplayViewer internal constructor(
    private val location: Path,
    val connection: ServerGamePacketListenerImpl
) {
    private val replay = ZipReplayFile(ReplayStudio(), this.location.toFile())
    private val markers by lazy { this.readMarkers() }

    private var started = false
    private var teleported = false

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    private val chunks = Collections.synchronizedCollection(LongOpenHashSet())
    private val entities = Collections.synchronizedCollection(IntOpenHashSet())
    private val players = Collections.synchronizedList(ArrayList<UUID>())
    private val objectives = Collections.synchronizedCollection(ArrayList<String>())

    private val bossbar = ServerBossEvent(Component.empty(), BossBarColor.BLUE, BossBarOverlay.PROGRESS)

    private var previousPack: ClientboundResourcePackPacket? = null

    private val duration = this.replay.metaData.duration.milliseconds
    private var progress = Duration.ZERO

    private var target = Duration.ZERO

    val server: MinecraftServer
        get() = this.player.server

    val player: ServerPlayer
        get() = this.connection.player

    var speedMultiplier = 1.0F
        private set
    var paused: Boolean = false
        private set

    fun start() {
        if (this.started) {
            return
        }
        if (this.connection.getViewingReplay() != null) {
            ServerReplay.logger.error("Player ${this.player.scoreboardName} tried watching 2 replays at once?!")
            return
        }

        this.started = true
        this.setForReplayView()

        this.restart()
    }

    fun stop() {
        this.close()

        this.removeReplayState()
        this.addBackToServer()

        ReplayViewers.remove(this.player.uuid)
    }

    fun restart() {
        if (!this.started) {
            return
        }
        this.removeReplayState()
        this.coroutineScope.coroutineContext.cancelChildren()
        this.teleported = false
        this.target = Duration.ZERO

        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createAddPacket(this.bossbar))
        }

        this.coroutineScope.launch {
            // Un-lazy the markers
            markers

            streamReplay { this.isActive }
        }
    }

    fun close() {
        this.coroutineScope.coroutineContext.cancelChildren()
        this.connection.stopViewingReplay()

        try {
            this.replay.close()
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to close replay file being viewed at ${this.location}")
        }
        try {
            val caches = this.location.parent.resolve(this.location.name + ".cache")
            @OptIn(ExperimentalPathApi::class)
            caches.deleteRecursively()
        } catch (e: IOException) {
            ServerReplay.logger.error("Failed to delete caches", e)
        }
    }

    fun jumpTo(timestamp: Duration): Boolean {
        if (timestamp.isNegative() || timestamp > this.duration) {
            return false
        }

        if (this.progress > timestamp) {
            this.restart()
        }
        this.target = timestamp
        return true
    }

    fun jumpToMarker(name: String?, offset: Duration): Boolean {
        val markers = this.markers[name]
        if (markers.isEmpty()) {
            return false
        }
        val marker = markers.firstOrNull { it.time.milliseconds > this.progress } ?: markers.first()
        return this.jumpTo(marker.time.milliseconds + offset)
    }

    fun getMarkers(): List<Marker> {
        return this.markers.values().sortedBy { it.time }
    }

    fun setSpeed(speed: Float) {
        if (speed <= 0) {
            throw IllegalArgumentException("Cannot set non-positive speed multiplier!")
        }
        this.speedMultiplier = speed
        this.sendTickingState()
    }

    fun setPaused(paused: Boolean): Boolean {
        if (this.paused == paused) {
            return false
        }
        this.paused = paused
        this.sendTickingState()
        this.updateProgress(this.progress)
        return true
    }

    fun showProgress(): Boolean {
        if (!this.bossbar.isVisible) {
            this.bossbar.isVisible = true
            this.send(ClientboundBossEventPacket.createAddPacket(this.bossbar))
            return true
        }
        return false
    }

    fun hideProgress(): Boolean {
        if (this.bossbar.isVisible) {
            this.bossbar.isVisible = false
            this.send(ClientboundBossEventPacket.createRemovePacket(this.bossbar.id))
            return true
        }
        return false
    }

    fun onServerboundPacket(packet: Packet<*>) {
        // To allow other packets, make sure you add them to the allowed packets in ReplayViewerPackets
        when (packet) {
            is ServerboundChatCommandPacket -> ReplayViewerCommands.handleCommand(packet.command, this)
        }
    }

    fun getResourcePack(hash: String): InputStream? {
        return this.replay.getResourcePack(hash).orNull()
    }

    private fun readMarkers(): Multimap<String?, Marker> {
        val markers = this.replay.markers.orNull()
        if (markers.isNullOrEmpty()) {
            return ImmutableMultimap.of()
        }

        val multimap = TreeMultimap.create<String?, Marker>(
            Comparator.nullsFirst<String?>(Comparator.naturalOrder()),
            Comparator.comparingInt(Marker::getTime)
        )
        for (marker in markers) {
            multimap.put(marker.name, marker)
        }
        return multimap
    }

    private suspend fun streamReplay(active: Supplier<Boolean>) {
        val version = ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion())

        this.replay.getPacketData(PacketTypeRegistry.get(version, State.CONFIGURATION)).use { stream ->
            this.sendPackets(stream, active)
        }
    }

    private suspend fun sendPackets(stream: ReplayInputStream, active: Supplier<Boolean>) {
        var lastTime = -1L
        var data: PacketData? = stream.readPacket()
        while (data != null && active.get()) {
            val progress = data.time.milliseconds
            if (lastTime != -1L && progress > this.target) {
                delay(((data.time - lastTime) / this.speedMultiplier).toLong())
            }

            while (this.paused) {
                delay(50)
            }

            when (data.packet.registry.state) {
                State.PLAY -> {
                    this.sendPlayPacket(data, active)
                    this.updateProgress(progress)
                    lastTime = data.time
                }
                else -> { }
            }

            data.release()
            data = stream.readPacket()
        }
        // Release any remaining data
        data?.release()
    }

    private fun sendPlayPacket(data: PacketData, active: Supplier<Boolean>) {
        val packet = data.packet.toClientboundPlayPacket()

        if (this.shouldSendPacket(packet)) {
            val modified = modifyPacketForViewer(packet)
            this.onSendPacket(modified)
            if (!active.get()) {
                return
            }
            this.send(modified)
            this.afterSendPacket(modified)
        }
    }

    private fun updateProgress(progress: Duration) {
        val title = Component.empty()
            .append(Component.literal(this.location.nameWithoutExtension).withStyle(ChatFormatting.GREEN))
            .append(" ")
            .append(Component.literal(progress.formatHHMMSS()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        if (this.paused) {
            title.append(Component.literal(" (PAUSED)").withStyle(ChatFormatting.DARK_AQUA))
        }
        this.bossbar.name = title

        this.progress = progress
        this.bossbar.progress = progress.div(this.duration).toFloat()

        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createUpdateProgressPacket(this.bossbar))
            this.send(ClientboundBossEventPacket.createUpdateNamePacket(this.bossbar))
        }
    }

    private fun sendTickingState() {

    }

    private fun setForReplayView() {
        this.removeFromServer()
        this.connection.startViewingReplay(this)

        this.removeServerState()
        ReplayViewerCommands.sendCommandPacket(this::send)
    }

    private fun addBackToServer() {
        val player = this.player
        val server = player.server
        val playerList = server.playerList
        val level = player.serverLevel()

        playerList.broadcastAll(
            ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(listOf(player))
        )
        playerList.players.add(player)

        RejoinedReplayPlayer.place(player, this.connection, afterLogin = {
            this.synchronizeClientLevel()
        })

        (player as EntityInvoker).removeRemovalReason()
        level.addNewPlayer(player)

        val previous = this.previousPack
        if (previous != null) {
            this.connection.send(previous)
        }

        player.inventoryMenu.sendAllDataToRemote()
        this.connection.send(ClientboundSetHealthPacket(
            player.health,
            player.foodData.foodLevel,
            player.foodData.saturationLevel
        ))
        this.connection.send(ClientboundSetExperiencePacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ))
    }

    private fun removeFromServer() {
        val player = this.player
        val playerList = player.server.playerList
        playerList.broadcastAll(ClientboundPlayerInfoRemovePacket(listOf(player.uuid)))
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION)
        playerList.players.remove(player)
    }

    private fun removeServerState() {
        val player = this.player
        val server = player.server
        this.send(ClientboundPlayerInfoRemovePacket(server.playerList.players.map { it.uuid }))
        MathUtils.forEachChunkAround(player.chunkPosition(), server.playerList.viewDistance) {
            this.send(ClientboundForgetLevelChunkPacket(it.x, it.z))
        }
        for (i in 0..18) {
            this.send(ClientboundSetDisplayObjectivePacket(i, null))
        }
        for (objective in server.scoreboard.objectives) {
            this.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
        }
        for (bossbar in server.customBossEvents.events) {
            if (bossbar.players.contains(player)) {
                this.send(ClientboundBossEventPacket.createRemovePacket(bossbar.id))
            }
        }

        val previous = (this.connection as PackTracker).`replay$getPack`()
        if (previous != null && previous !== EMPTY_PACK) {
            this.previousPack = previous
            this.send(EMPTY_PACK)
        }
    }

    private fun removeReplayState() {
        synchronized(this.players) {
            this.send(ClientboundPlayerInfoRemovePacket(this.players))
        }
        synchronized(this.entities) {
            this.send(ClientboundRemoveEntitiesPacket(IntArrayList(this.entities)))
        }
        synchronized(this.chunks) {
            for (chunk in this.chunks.iterator()) {
                this.connection.send(ClientboundForgetLevelChunkPacket(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)))
            }
        }
        synchronized(this.objectives) {
            for (objective in this.objectives) {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                val dummy = Objective(
                    null,
                    objective,
                    ObjectiveCriteria.DUMMY,
                    Component.empty(),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null
                )
                this.send(ClientboundSetObjectivePacket(dummy, ClientboundSetObjectivePacket.METHOD_REMOVE))
            }
        }

        this.send(EMPTY_PACK)

        if (this.bossbar.isVisible) {
            this.send(ClientboundBossEventPacket.createRemovePacket(this.bossbar.id))
        }
    }

    private fun shouldSendPacket(packet: Packet<*>): Boolean {
        return when (packet) {
            is ClientboundGameEventPacket -> packet.event != CHANGE_GAME_MODE
            is ClientboundPlayerPositionPacket -> {
                // We want the client to teleport to the first initial position
                // subsequent positions will teleport the viewer which we don't want
                val teleported = this.teleported
                this.teleported = true
                return !teleported
            }
            else -> true
        }
    }

    private fun onSendPacket(packet: Packet<*>) {
        // We keep track of some state to revert later
        when (packet) {
            is ClientboundLevelChunkWithLightPacket -> this.chunks.add(ChunkPos.asLong(packet.x, packet.z))
            is ClientboundForgetLevelChunkPacket -> this.chunks.remove(ChunkPos.asLong(packet.x, packet.z))
            is ClientboundAddPlayerPacket -> this.entities.add(packet.entityId)
            is ClientboundAddEntityPacket -> this.entities.add(packet.id)
            is ClientboundRemoveEntitiesPacket -> this.entities.removeAll(packet.entityIds)
            is ClientboundSetObjectivePacket -> {
                if (packet.method == ClientboundSetObjectivePacket.METHOD_REMOVE) {
                    this.objectives.remove(packet.objectiveName)
                } else {
                    this.objectives.add(packet.objectiveName)
                }
            }
            is ClientboundPlayerInfoUpdatePacket -> {
                for (entry in packet.newEntries()) {
                    this.players.add(entry.profileId)
                }
            }
            is ClientboundPlayerInfoRemovePacket -> this.players.removeAll(packet.profileIds)
            is ClientboundRespawnPacket -> this.teleported = false
        }
    }

    private fun afterSendPacket(packet: Packet<*>) {
        when (packet) {
            is ClientboundLoginPacket -> {
                this.synchronizeClientLevel()
                this.send(ClientboundGameEventPacket(CHANGE_GAME_MODE, GameType.SPECTATOR.id.toFloat()))
            }
            is ClientboundRespawnPacket -> {
                this.send(ClientboundGameEventPacket(CHANGE_GAME_MODE, GameType.SPECTATOR.id.toFloat()))
            }
        }
    }

    private fun modifyPacketForViewer(packet: Packet<*>): Packet<*> {
        if (packet is ClientboundLoginPacket) {
            // Give the viewer a different ID to not conflict
            // with any entities in the replay
            return ClientboundLoginPacket(
                VIEWER_ID,
                packet.hardcore,
                packet.gameType,
                packet.previousGameType,
                packet.levels,
                packet.registryHolder,
                packet.dimensionType,
                packet.dimension,
                packet.seed,
                packet.maxPlayers,
                packet.chunkRadius,
                packet.simulationDistance,
                packet.reducedDebugInfo,
                packet.showDeathScreen,
                packet.isDebug,
                packet.isFlat,
                packet.lastDeathLocation,
                packet.portalCooldown
            )
        }
        if (packet is ClientboundPlayerInfoUpdatePacket) {
            val copy = ArrayList(packet.entries())
            if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT)) {
                val iter = copy.listIterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    iter.set(ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId,
                        entry.profile,
                        entry.listed,
                        entry.latency,
                        entry.gameMode,
                        entry.displayName,
                        null
                    ))
                }
            }

            val index = packet.entries().indexOfFirst { it.profileId == this.player.uuid }
            if (index >= 0) {
                val previous = copy[index]
                copy[index] = ClientboundPlayerInfoUpdatePacket.Entry(
                    VIEWER_UUID,
                    previous.profile,
                    previous.listed,
                    previous.latency,
                    previous.gameMode,
                    previous.displayName,
                    null
                )
            }
            return ReplayViewerUtils.createClientboundPlayerInfoUpdatePacket(packet.actions(), copy)
        }
        if (packet is ClientboundAddPlayerPacket && packet.playerId == this.player.uuid) {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            buf.writeVarInt(packet.entityId)
            buf.writeUUID(VIEWER_UUID)
            buf.writeDouble(packet.x)
            buf.writeDouble(packet.y)
            buf.writeDouble(packet.z)
            buf.writeByte(packet.getyRot().toInt())
            buf.writeByte(packet.getxRot().toInt())
            val playerPacket = ClientboundAddPlayerPacket(buf)
            buf.release()
            return playerPacket
        }
        if (packet is ClientboundPlayerChatPacket) {
            // We don't want to deal with chat validation...
            val message = packet.unsignedContent ?: Component.literal(packet.body.content)
            val type = packet.chatType.resolve(this.player.server.registryAccess())
            val decorated = if (type.isPresent) {
                type.get().decorate(message)
            } else {
                Component.literal("<").append(packet.chatType.name).append("> ").append(message)
            }
            return ClientboundSystemChatPacket(decorated, false)
        }

        if (packet is ClientboundResourcePackPacket && packet.url.startsWith("replay://")) {
            val request = packet.url.removePrefix("replay://").toIntOrNull()
                ?: throw IllegalStateException("Malformed replay packet url")
            val hash = this.replay.resourcePackIndex[request]
                ?: throw IllegalStateException("Unknown replay resource pack index")
            val url = DownloadPacksHttpInjector.createUrl(this, hash)
            return ClientboundResourcePackPacket(url, "", packet.isRequired, packet.prompt)
        }

        return packet
    }

    private fun synchronizeClientLevel() {
        val level = this.player.serverLevel()
        this.send(ClientboundRespawnPacket(
            level.dimensionTypeId(),
            level.dimension(),
            BiomeManager.obfuscateSeed(level.seed),
            this.player.gameMode.gameModeForPlayer,
            this.player.gameMode.previousGameModeForPlayer,
            level.isDebug,
            level.isFlat,
            ClientboundRespawnPacket.KEEP_ALL_DATA,
            this.player.lastDeathLocation,
            this.player.portalCooldown
        ))
    }

    internal fun send(packet: Packet<*>) {
        this.connection.sendReplayPacket(packet)
    }

    private companion object {
        const val VIEWER_ID = Int.MAX_VALUE - 10
        val VIEWER_UUID: UUID = UUIDUtil.createOfflinePlayerUUID("-ViewingProfile-")

        val EMPTY_PACK = ClientboundResourcePackPacket(
            "https://static.planetminecraft.com/files/resource_media/texture/nothing.zip",
            "",
            false,
            null
        )
    }
}