package me.senseiwells.replay.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.sun.net.httpserver.HttpServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

abstract class HttpHost(ip: String?, port: Int?) {
    protected val logger: Logger = LoggerFactory.getLogger(this.getName())
    private val builder = ThreadFactoryBuilder().setNameFormat("${this.getName()}-%d").build()

    val ip: String = ip ?: getLocalIp()
    val port: Int = port ?: 8080

    private var server: HttpServer? = null
    private var pool: ExecutorService? = null
    private var future: CompletableFuture<Boolean>? = null

    var threads: Int = 3

    val running: Boolean
        get() = this.server != null

    fun getUrl(): String {
        @Suppress("HttpUrlsUsage")
        return "http://${this.ip}:${this.port}"
    }

    open fun getName(): String {
        return this::class.java.simpleName
    }

    fun start(): CompletableFuture<Boolean> {
        this.future?.cancel(true)

        val restart = this.server !== null
        this.server?.stop(0)
        this.pool?.shutdownNow()

        this.pool = Executors.newFixedThreadPool(this.threads, this.builder)
        val future = CompletableFuture.supplyAsync({
            try {
                this.logger.info("${if (restart) "Restarting" else "Starting"} ${this.getName()}...")

                val server = HttpServer.create(InetSocketAddress("0.0.0.0", this.port), 0)
                server.executor = this.pool

                val futures = LinkedList<CompletableFuture<Void>>()
                this.onStart(server) { task ->
                    futures.add(CompletableFuture.runAsync(task, this.pool))
                }
                futures.forEach { it.join() }

                server.start()
                this.server = server
                this.logger.info("${this.getName()} successfully started")
                true
            } catch (e: Exception) {
                this.logger.error("Failed to start ${this.getName()}!", e)
                false
            }
        }, this.pool)
        this.future = future
        return future
    }

    fun stop() {
        this.server?.stop(0)
        this.pool?.shutdownNow()
        this.future?.cancel(true)

        this.onStop()
    }

    protected open fun onStart(server: HttpServer, async: Consumer<Runnable>) {

    }

    protected open fun onStop() {

    }

    companion object {
        fun getLocalIp(): String {
            return InetAddress.getLocalHost().hostAddress
        }
    }
}