package me.senseiwells.replay.rejoin

import me.senseiwells.replay.ducks.`ServerReplay$PackTracker`
import me.senseiwells.replay.recorder.ReplayRecorder
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.scores.Objective
import kotlin.collections.HashSet

class RejoinedReplayPlayer private constructor(
    val original: ServerPlayer,
    val recorder: ReplayRecorder
): ServerPlayer(original.server, original.getLevel(), original.gameProfile) {
    init {
        this.id = this.original.id
    }

    private fun sendResourcePacks() {
        val connection = this.original.connection
        // Our connection may be null if we're using a fake player
        if (connection is `ServerReplay$PackTracker`) {
            val packet = connection.`replay$getPack`() ?: return
            this.recorder.record(packet)
        } else {
            val server = this.server
            if (server.resourcePack.isNotEmpty()) {
                this.sendTexturePack(
                    server.resourcePack,
                    server.resourcePackHash,
                    server.isResourcePackRequired,
                    server.resourcePackPrompt
                )
            }
        }
    }

    companion object {
        fun rejoin(player: ServerPlayer, recorder: ReplayRecorder) {
            recorder.afterLogin()

            val rejoined = RejoinedReplayPlayer(player, recorder)
            val connection = RejoinConnection()

            rejoined.load(player.saveWithoutId(CompoundTag()))
            place(rejoined, RejoinGamePacketListener(rejoined, connection), player, rejoined::sendResourcePacks) {
                recorder.shouldHidePlayerFromTabList(it)
            }
        }

        fun place(
            player: ServerPlayer,
            listener: ServerGamePacketListenerImpl,
            old: ServerPlayer = player,
            afterLogin: () -> Unit = {},
            shouldHidePlayer: (ServerPlayer) -> Boolean = { false }
        ) {
            val server = player.server
            val players = server.playerList
            val level = player.getLevel()
            val levelData = level.levelData
            val rules = level.gameRules
            listener.send(ClientboundLoginPacket(
                player.id,
                old.gameMode.gameModeForPlayer,
                old.gameMode.previousGameModeForPlayer,
                BiomeManager.obfuscateSeed(level.seed),
                levelData.isHardcore,
                server.levelKeys(),
                server.registryAccess() as RegistryAccess.RegistryHolder,
                level.dimensionType(),
                level.dimension(),
                players.maxPlayers,
                players.viewDistance,
                rules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO),
                !rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),
                level.isDebug,
                level.isFlat
            ))
            afterLogin()

            listener.send(ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.BRAND, PacketByteBufs.create().writeUtf(server.serverModName)))
            listener.send(ClientboundChangeDifficultyPacket(levelData.difficulty, levelData.isDifficultyLocked))
            listener.send(ClientboundPlayerAbilitiesPacket(player.abilities))
            listener.send(ClientboundSetCarriedItemPacket(player.inventory.selected))
            listener.send(ClientboundUpdateRecipesPacket(server.recipeManager.recipes))
            listener.send(ClientboundUpdateTagsPacket(server.tags.serializeToNetwork(server.registryAccess())))
            players.sendPlayerPermissionLevel(player)

            player.recipeBook.sendInitialRecipeBook(player)

            val scoreboard = server.scoreboard
            for (playerTeam in scoreboard.playerTeams) {
                listener.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true))
            }

            val set = HashSet<Objective>()
            for (i in 0..18) {
                val objective = scoreboard.getDisplayObjective(i)
                if (objective != null && !set.contains(objective)) {
                    for (packet in scoreboard.getStartTrackingPackets(objective)) {
                        listener.send(packet)
                    }
                    set.add(objective)
                }
            }

            listener.teleport(player.x, player.y, player.z, player.yRot, player.xRot)

            // We do this to ensure that we have ALL the players
            // including any 'fake' chunk players
            val uniques = HashSet(players.players)
            if (!uniques.contains(old)) {
                uniques.add(player)
            }

            listener.send(ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, uniques))

            players.sendLevelInfo(player, level)

            for (event in server.customBossEvents.events) {
                if (event.players.contains(old) && event.isVisible) {
                    listener.send(ClientboundBossEventPacket.createAddPacket(event))
                }
            }

            for (mobEffectInstance in player.activeEffects) {
                listener.send(ClientboundUpdateMobEffectPacket(player.id, mobEffectInstance))
            }
        }
    }
}