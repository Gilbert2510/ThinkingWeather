package com.thinkingweather.gateway

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

interface WeatherSource {
    fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long = 0): Cached<JsonObject>
}

class QWeatherClient(private val host: String, private val auth: UpstreamAuth, requestsPerMinute: Int = 180) : WeatherSource, AutoCloseable {
    private val executor = ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue(64), ThreadPoolExecutor.CallerRunsPolicy())
    private val permits = Semaphore(24)
    private val requestBudget = RateLimiter(perPeerLimit = requestsPerMinute, globalLimit = requestsPerMinute, maximumPeers = 1)
    private val cache = BoundedCache<JsonObject>()
    private val json = Json { isLenient = false }
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .executor(executor)
        .version(HttpClient.Version.HTTP_2)
        .build()

    init { validateApiHost(host) }

    override fun get(path: String, parameters: Map<String, String>, ttlMillis: Long, staleMillis: Long): Cached<JsonObject> {
        // Paths come exclusively from service constants and validated coordinates, never from the HTTP request URL.
        require(path.startsWith('/') && path.none { it in "?#@\\" } && !path.contains(".."))
        fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
        val query = parameters.toSortedMap().entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val key = "$path?$query"
        return cache.get(key, ttlMillis, staleMillis) { fetch(URI("https://$host$key")) }
    }

    private fun fetch(uri: URI): JsonObject {
        if (!requestBudget.allow("upstream")) throw GatewayException(503, "provider_budget_exceeded")
        if (!permits.tryAcquire()) throw GatewayException(503, "gateway_busy")
        try {
            val (headerName, headerValue) = auth.header()
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header(headerName, headerValue)
                .GET().build()
            // The additional completion timeout covers a peer that sends headers then stalls the response body.
            val future = client.sendAsync(request) { BoundedBodySubscriber(512 * 1024) }
            val response = try { future.get(8, TimeUnit.SECONDS) } catch (_: Exception) {
                future.cancel(true)
                throw GatewayException(502, "upstream_unavailable")
            }
            if (response.statusCode() != 200) throw GatewayException(502, when (response.statusCode()) {
                401, 403 -> "upstream_credentials_or_plan"
                429 -> "upstream_quota_exceeded"
                404 -> "upstream_not_found"
                else -> "upstream_unavailable"
            })
            val encoding = response.headers().firstValue("Content-Encoding").orElse("").lowercase()
            val input = when (encoding) {
                "gzip" -> try { GZIPInputStream(ByteArrayInputStream(response.body())) } catch (_: Exception) { throw GatewayException(502, "invalid_upstream_response") }
                "", "identity" -> ByteArrayInputStream(response.body())
                else -> throw GatewayException(502, "invalid_upstream_response")
            }
            val body = input.use { stream -> stream.readNBytes(1024 * 1024 + 1) }
            if (body.size > 1024 * 1024) throw GatewayException(502, "upstream_response_too_large")
            val parsed = try { json.parseToJsonElement(body.toString(Charsets.UTF_8)) as? JsonObject } catch (_: Exception) { null }
                ?: throw GatewayException(502, "invalid_upstream_response")
            val code = (parsed["code"] as? JsonPrimitive)?.contentOrNull
            if (code != null && code !in setOf("200", "204")) throw GatewayException(502, when (code) {
                "401", "403" -> "upstream_credentials_or_plan"
                "429", "402" -> "upstream_quota_exceeded"
                "404" -> "upstream_not_found"
                else -> "upstream_unavailable"
            })
            return parsed
        } catch (exception: GatewayException) { throw exception
        } catch (_: Exception) { throw GatewayException(502, "upstream_unavailable")
        } finally { permits.release() }
    }

    override fun close() { executor.shutdownNow() }
}

/** Enforces the compressed size while receiving, before allocating an unbounded body. */
internal class BoundedBodySubscriber(private val maximumBytes: Int) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val output = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null
    override fun getBody(): CompletionStage<ByteArray> = result
    override fun onSubscribe(value: Flow.Subscription) { subscription = value; value.request(1) }
    override fun onNext(buffers: MutableList<ByteBuffer>) {
        for (buffer in buffers) {
            if (buffer.remaining() > maximumBytes - output.size()) {
                subscription?.cancel()
                result.completeExceptionally(GatewayException(502, "upstream_response_too_large"))
                return
            }
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            output.write(bytes)
        }
        subscription?.request(1)
    }
    override fun onError(throwable: Throwable) { result.completeExceptionally(throwable) }
    override fun onComplete() { result.complete(output.toByteArray()) }
}
