package me.senseiwells.replay.util

import io.netty.buffer.ByteBuf

fun ByteBuf.toByteArray(): ByteArray {
    val bytes = ByteArray(this.readableBytes())
    this.readBytes(bytes)
    return bytes
}