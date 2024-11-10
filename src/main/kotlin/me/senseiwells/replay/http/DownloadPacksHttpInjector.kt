package me.senseiwells.replay.http

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.ChunkedStream
import me.senseiwells.replay.ServerReplay
import me.senseiwells.replay.viewer.ReplayViewer
import me.senseiwells.replay.viewer.ReplayViewers
import net.mcbrawls.inject.api.InjectorContext
import net.mcbrawls.inject.http.HttpByteBuf
import net.mcbrawls.inject.http.HttpInjector
import net.mcbrawls.inject.http.HttpRequest
import java.io.InputStream
import java.nio.charset.StandardCharsets

object DownloadPacksHttpInjector: HttpInjector() {
    private val regex = Regex("""^/replay/packs/(\d+)/([0-9a-f]{5,40})$""")

    fun createUrl(viewer: ReplayViewer, hash: String): String {
        val identity = System.identityHashCode(viewer)
        return "http://${ServerReplay.getIp(viewer.server)}/replay/packs/${identity}/$hash"
    }

    override fun isRelevant(ctx: InjectorContext, request: HttpRequest): Boolean {
        return request.requestURI.matches(this.regex)
    }

    override fun onRead(ctx: ChannelHandlerContext, buf: ByteBuf): Boolean {
        val request = HttpRequest.parse(buf)
        val match = this.regex.find(request.requestURI)!!
        val identity = match.groups[1]!!.value.toInt()
        val hash = match.groups[2]!!.value

        val viewer = ReplayViewers.viewers().find { System.identityHashCode(it) == identity }
            ?: return super.onRead(ctx, buf)
        val stream = viewer.getResourcePack(hash)
            ?: return super.onRead(ctx, buf)

        this.download(ctx, stream)
        return true
    }

    override fun intercept(ctx: ChannelHandlerContext, request: HttpRequest): HttpByteBuf {
        val buf = HttpByteBuf.httpBuf(ctx)
        buf.writeStatusLine("1.1", 404, "Not Found")
        return buf
    }

    private fun download(ctx: ChannelHandlerContext, stream: InputStream) {
        val buf = HttpByteBuf.httpBuf(ctx)
        buf.writeStatusLine("1.1", 200, "OK")
        buf.writeHeader("user-agent", "kotlin/replay-pack-download-host")
        buf.writeHeader("content-type", "application/octet-stream")
        buf.writeHeader("transfer-encoding", "chunked")
        buf.writeText("")
        ctx.writeAndFlush(buf.inner())

        val chunked = ChunkedStream(stream)
        ctx.writeAndFlush(chunked)

        var read: Int
        val buffer = ByteArray(8192)
        while (true) {
            read = stream.read(buffer)
            if (read == -1) {
                break
            }

            val chunkHeader = "${read.toString(16)}\r\n"
            ctx.write(Unpooled.copiedBuffer(chunkHeader, StandardCharsets.US_ASCII))
            ctx.write(Unpooled.copiedBuffer(buffer, 0, read))
            ctx.write(Unpooled.copiedBuffer("\r\n", StandardCharsets.US_ASCII))
        }

        ctx.writeAndFlush(Unpooled.copiedBuffer("0\r\n\r\n", StandardCharsets.US_ASCII))

        stream.close()
    }
}