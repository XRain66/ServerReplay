package me.senseiwells.replay.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.senseiwells.replay.ServerReplay
import net.casual.arcade.host.core.HttpHost
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.*

class DownloadHost(ip: String?, port: Int): HttpHost(ip, port, 3) {
    override fun getName(): String {
        return "replay-download-host"
    }

    override fun onStart(server: HttpServer) {
        server.createContext("/player", this::handlePlayerDownloadRequest)
        server.createContext("/chunk", this::handleChunkDownloadRequest)
    }

    override fun onStop() {

    }

    private fun handlePlayerDownloadRequest(exchange: HttpExchange) {
        val path = URLDecoder.decode(exchange.requestURI.rawPath, StandardCharsets.UTF_8)
            .removePrefix("/player/")
        this.handleDownloadRequest(exchange, ServerReplay.config.playerRecordingPath.resolve(path))
    }

    private fun handleChunkDownloadRequest(exchange: HttpExchange) {
        val path = URLDecoder.decode(exchange.requestURI.rawPath, StandardCharsets.UTF_8)
            .removePrefix("/chunk/")
        this.handleDownloadRequest(exchange, ServerReplay.config.chunkRecordingPath.resolve(path))
    }

    private fun handleDownloadRequest(exchange: HttpExchange, path: Path) {
        if ("GET" == exchange.requestMethod && path.isReadable() && path.extension == "mcpr") {
            exchange.responseHeaders.add("User-Agent", "Kotlin/ReplayDownloadHost")
            try {
                exchange.sendResponseHeaders(200, path.fileSize())
                exchange.responseBody.use { response ->
                    path.inputStream().use { stream ->
                        stream.transferTo(response)
                    }
                }
            } catch (e: IOException) {
                exchange.sendResponseHeaders(500, -1)
            }
        } else {
            exchange.sendResponseHeaders(400, -1)
        }
        exchange.close()
    }
}