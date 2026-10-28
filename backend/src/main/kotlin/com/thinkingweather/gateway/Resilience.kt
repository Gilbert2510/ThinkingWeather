package com.thinkingweather.gateway

import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class GatewayException(val status: Int, val code: String) : RuntimeException(code)

data class Cached<T>(val value: T, val stale: Boolean, val ageSeconds: Long)

/** Bounded LRU with single flight, failure cooldown and explicitly bounded stale reuse. */
class BoundedCache<T : Any>(
    private val maximumEntries: Int = 512,
    private val maximumLoads: Int = 64,
    private val failureCooldownMillis: Long = 10_000,
    private val timeMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Record<T>(val value: T?, val savedAt: Long, val failureAt: Long?, val failure: GatewayException?)
    private val records = LinkedHashMap<String, Record<T>>(16, 0.75f, true)
    private val inFlight = HashMap<String, CompletableFuture<Cached<T>>>()
    private val lock = Any()

    init { require(maximumEntries > 0 && maximumLoads > 0 && failureCooldownMillis >= 0) }

    fun get(key: String, ttlMillis: Long, staleMillis: Long = 0, load: () -> T): Cached<T> {
        require(ttlMillis >= 0 && staleMillis >= 0)
        val flight: CompletableFuture<Cached<T>>
        val owner: Boolean
        synchronized(lock) {
            val now = timeMillis()
            val record = records[key]
            val age = record?.let { (now - it.savedAt).coerceAtLeast(0) } ?: Long.MAX_VALUE
            if (record?.value != null && age < ttlMillis) return Cached(record.value, false, age / 1000)
            if (record?.failureAt != null && now - record.failureAt < failureCooldownMillis) {
                if (record.value != null && age < ttlMillis + staleMillis) return Cached(record.value, true, age / 1000)
                throw record.failure ?: GatewayException(502, "upstream_unavailable")
            }
            val existing = inFlight[key]
            if (existing != null) {
                flight = existing
                owner = false
            } else {
                if (inFlight.size >= maximumLoads) throw GatewayException(503, "gateway_busy")
                flight = CompletableFuture()
                inFlight[key] = flight
                owner = true
            }
        }
        if (owner) {
            try {
                val value = load()
                synchronized(lock) { put(key, Record(value, timeMillis(), null, null)) }
                flight.complete(Cached(value, false, 0))
            } catch (exception: Exception) {
                val failure = (exception as? GatewayException) ?: GatewayException(502, "upstream_unavailable")
                val stale = synchronized(lock) {
                    val now = timeMillis()
                    val previous = records[key]
                    put(key, Record(previous?.value, previous?.savedAt ?: now, now, failure))
                    val age = previous?.let { (now - it.savedAt).coerceAtLeast(0) } ?: Long.MAX_VALUE
                    if (previous?.value != null && age < ttlMillis + staleMillis) Cached(previous.value, true, age / 1000) else null
                }
                if (stale != null) flight.complete(stale) else flight.completeExceptionally(failure)
            } finally {
                synchronized(lock) { inFlight.remove(key) }
            }
        }
        try { return flight.join() } catch (exception: CompletionException) {
            throw (exception.cause as? GatewayException ?: GatewayException(502, "upstream_unavailable"))
        }
    }

    private fun put(key: String, record: Record<T>) {
        records[key] = record
        while (records.size > maximumEntries) records.entries.iterator().run { next(); remove() }
    }

    internal fun size(): Int = synchronized(lock) { records.size }
}

/** Fixed windows, socket peer identity only; forwarded headers cannot bypass this limiter. */
class RateLimiter(
    private val perPeerLimit: Int = 60,
    private val globalLimit: Int = 600,
    private val maximumPeers: Int = 4096,
    private val windowMillis: Long = 60_000,
    private val timeMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Counter(var startedAt: Long, var count: Int)
    private val peers = HashMap<String, Counter>()
    private var global = Counter(timeMillis(), 0)

    init { require(perPeerLimit > 0 && globalLimit > 0 && maximumPeers > 0 && windowMillis > 0) }

    @Synchronized fun allow(peer: String): Boolean {
        val now = timeMillis()
        if (now - global.startedAt >= windowMillis) global = Counter(now, 0)
        if (global.count >= globalLimit) return false
        var counter = peers[peer]
        if (counter == null) {
            if (peers.size >= maximumPeers) peers.entries.removeIf { now - it.value.startedAt >= windowMillis }
            if (peers.size >= maximumPeers) return false
            counter = Counter(now, 0)
            peers[peer] = counter
        }
        if (now - counter.startedAt >= windowMillis) { counter.startedAt = now; counter.count = 0 }
        if (counter.count >= perPeerLimit) return false
        counter.count++
        global.count++
        return true
    }

    internal fun size(): Int = peers.size
}
