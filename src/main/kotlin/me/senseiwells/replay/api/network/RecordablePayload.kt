package me.senseiwells.replay.api.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Interface for [CustomPacketPayload]s to indicate whether
 * they should be recorded with ServerReplay or not.
 */
interface RecordablePayload {
    /**
     * Whether this payload should be recorded
     * by ServerReplay.
     *
     * @return Whether the payload should be recorded.
     */
    fun shouldRecord(): Boolean
}