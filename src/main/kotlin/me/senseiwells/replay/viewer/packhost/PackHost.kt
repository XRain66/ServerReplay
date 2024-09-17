package me.senseiwells.replay.viewer.packhost

import com.google.common.hash.Hashing
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import me.senseiwells.replay.util.HttpHost
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class PackHost(ip: String?, port: Int): HttpHost(ip, port) {
    private val hosted = ConcurrentHashMap<String, HostedPack>()
    private val packs = ArrayList<ReadablePack>()

    init {
        this.threads = 1
    }

    fun addPack(packs: ReadablePack) {
        this.packs.add(packs)
    }

    fun getHostedPack(name: String): HostedPack? {
        val zipped = if (name.endsWith(".zip")) name else "$name.zip"
        return this.hosted[zipped]
    }

    override fun getName(): String {
        return "ResourcePackHost"
    }

    override fun onStart(server: HttpServer, async: Consumer<Runnable>) {
        for (pack in this.packs) {
            async.accept {
                val name = pack.name
                val url = "${this.getUrl()}/${name}"

                @Suppress("DEPRECATION")
                val hash = Hashing.sha1().hashBytes(pack.stream().use(InputStream::readBytes)).toString()

                val hosted = HostedPack(pack, url, hash)
                val zipped = if (pack.name.endsWith(".zip")) pack.name else "${pack.name}.zip"
                this.hosted[zipped] = hosted

                server.createContext("/$name", Handler(pack))

                this.logger.info("Hosting pack: ${pack.name}: $url")
            }
        }
    }

    override fun onStop() {
        this.packs.clear()
        this.hosted.clear()
    }

    private class Handler(val pack: ReadablePack): HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if ("GET" == exchange.requestMethod && this.pack.readable()) {
                exchange.responseHeaders.add("User-Agent", "Kotlin/ResourcePackHost")
                exchange.sendResponseHeaders(200, this.pack.length())
                exchange.responseBody.use { response ->
                    this.pack.stream().use { stream ->
                        stream.transferTo(response)
                    }
                }
            } else {
                exchange.sendResponseHeaders(400, -1)
            }
            exchange.close()
        }
    }
}