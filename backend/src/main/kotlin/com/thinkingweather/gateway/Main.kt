package com.thinkingweather.gateway

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

fun main() {
    // Never print configuration values, credentials, upstream bodies or user locations.
    val config = try { Config.fromEnvironment() } catch (_: Exception) {
        System.err.println("Gateway configuration is invalid; check variable names and formats in backend/README.md.")
        exitProcess(1)
    }
    val gateway = GatewayServer(config)
    Runtime.getRuntime().addShutdownHook(Thread { gateway.close() })
    gateway.start()
    println("Weather gateway started; configured=${config.ready} port=${config.port}.")
}

class GatewayServer(
    private val config: Config,
    private val limiter: RateLimiter = RateLimiter(config.perPeerRequestsPerMinute, config.globalRequestsPerMinute),
    sourceOverride: WeatherSource? = null,
    addressOverride: InetSocketAddress? = null,
) : AutoCloseable {
    private val client = if (sourceOverride == null && config.ready) QWeatherClient(config.apiHost!!, config.auth!!, config.upstreamRequestsPerMinute) else null
    private val service = (sourceOverride ?: client)?.let {
        WeatherService(it, config.weatherApi, config.enableAir, config.enableWarnings, enableIndices = config.enableIndices)
    }
    private val workers = ThreadPoolExecutor(12, 12, 30, TimeUnit.SECONDS, ArrayBlockingQueue(48), ThreadPoolExecutor.AbortPolicy())
    private val active = Semaphore(10)
    private val server = HttpServer.create(addressOverride ?: InetSocketAddress(config.bindAddress, config.port), 32).apply {
        executor = workers
        createContext("/") { exchange -> handle(exchange) }
    }
    val port get() = server.address.port
    fun start() = server.start()

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            var acquired = false
            try {
                if (exchange.requestMethod != "GET") {
                    exchange.responseHeaders.set("Allow", "GET")
                    throw GatewayException(405, "method_not_allowed")
                }
                if (exchange.requestURI.toASCIIString().length > 1024) throw GatewayException(414, "uri_too_long")
                // Reject bodies without reading or draining them; close the connection after replying.
                if (exchange.requestHeaders.getFirst("Transfer-Encoding") != null ||
                    exchange.requestHeaders.getFirst("Content-Length")?.let { it != "0" } == true) {
                    exchange.responseHeaders.set("Connection", "close")
                    throw GatewayException(400, "request_body_not_allowed")
                }
                if (exchange.requestURI.rawPath == "/health") {
                    if (exchange.requestURI.rawQuery != null) throw GatewayException(400, "invalid_query")
                    respond(exchange, 200, buildJsonObject { put("status", "ok"); put("configured", service != null) })
                    return
                }
                if (exchange.requestURI.rawPath !in setOf("/v1/cities", "/v1/current", "/v1/weather")) throw GatewayException(404, "not_found")
                val peer = exchange.remoteAddress.address.hostAddress
                if (!limiter.allow(peer)) {
                    exchange.responseHeaders.set("Retry-After", "60")
                    throw GatewayException(429, "rate_limited")
                }
                if (!active.tryAcquire()) throw GatewayException(503, "gateway_busy")
                acquired = true
                val weather = service ?: throw GatewayException(503, "provider_not_configured")
                val result = when (exchange.requestURI.rawPath) {
                    "/v1/cities" -> weather.cities(Validation.parameters(exchange.requestURI.rawQuery, "q"))
                    "/v1/current" -> weather.current(Validation.parameters(exchange.requestURI.rawQuery, "location"))
                    else -> weather.weather(Validation.parameters(exchange.requestURI.rawQuery, "location"))
                }
                respond(exchange, 200, result)
            } catch (error: GatewayException) {
                respondSafely(exchange, error.status, error.code)
            } catch (_: Exception) {
                respondSafely(exchange, 500, "internal_error")
            } finally { if (acquired) active.release() }
        }
    }

    private fun respondSafely(exchange: HttpExchange, status: Int, code: String) {
        try { respond(exchange, status, buildJsonObject { put("error", code) }) } catch (_: Exception) { /* disconnected client */ }
    }

    private fun respond(exchange: HttpExchange, status: Int, payload: JsonObject) {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() { server.stop(1); workers.shutdownNow(); service?.close(); client?.close() }
}
