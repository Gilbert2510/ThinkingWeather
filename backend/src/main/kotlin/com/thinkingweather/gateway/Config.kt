package com.thinkingweather.gateway

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Config(
    val bindAddress: String = "127.0.0.1",
    val port: Int = 8080,
    val apiHost: String? = null,
    val auth: UpstreamAuth? = null,
    val weatherApi: String = "v1",
    val enableAir: Boolean = true,
    val enableWarnings: Boolean = true,
    val perPeerRequestsPerMinute: Int = 60,
    val globalRequestsPerMinute: Int = 600,
    val upstreamRequestsPerMinute: Int = 180,
    val enableIndices: Boolean = true,
) {
    val ready get() = apiHost != null && auth != null

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): Config {
            fun value(name: String) = env[name]?.trim()?.takeIf(String::isNotEmpty)
            fun requests(name: String, default: Int): Int {
                val parsed = value(name)?.toIntOrNull() ?: if (value(name) == null) default else 0
                require(parsed in 1..6000) { "$name must be 1..6000" }
                return parsed
            }
            fun bool(name: String) = when (value(name)) {
                null, "true" -> true
                "false" -> false
                else -> error("$name must be true or false")
            }
            val host = value("QWEATHER_API_HOST")?.let(::validateApiHost)
            val jwtNames = listOf("QWEATHER_PRIVATE_KEY_FILE", "QWEATHER_KEY_ID", "QWEATHER_DEVELOPER_ID", "QWEATHER_PROJECT_ID")
            val jwt = jwtNames.map(::value)
            val apiKey = value("QWEATHER_API_KEY")
            require(!(apiKey != null && jwt.any { it != null })) { "Configure JWT or API KEY, never both" }
            require(jwt.all { it == null } || jwt.all { it != null }) { "JWT requires private key file, key ID, developer ID and project ID" }
            val auth = when {
                jwt.all { it != null } -> JwtAuth(readPrivateKey(Path.of(jwt[0]!!)), jwt[1]!!, jwt[2]!!, jwt[3]!!)
                apiKey != null -> ApiKeyAuth(apiKey)
                else -> null
            }
            require((host == null) == (auth == null)) { "QWEATHER_API_HOST and credentials must be configured together" }
            val api = value("QWEATHER_WEATHER_API") ?: "v1"
            require(api == "v1" || api == "v7") { "QWEATHER_WEATHER_API must be v1 or v7" }
            val port = (value("PORT") ?: "8080").toIntOrNull()
            require(port != null && port in 1..65535) { "PORT must be 1..65535" }
            return Config(
                value("BIND_ADDRESS") ?: "127.0.0.1", port, host, auth, api, bool("QWEATHER_ENABLE_AIR"), bool("QWEATHER_ENABLE_WARNINGS"),
                requests("GATEWAY_PER_PEER_RPM", 60), requests("GATEWAY_GLOBAL_RPM", 600), requests("QWEATHER_UPSTREAM_RPM", 180),
                enableIndices = bool("QWEATHER_ENABLE_INDICES"),
            )
        }
    }
}

/** Only the account's dedicated QWeather host is accepted. No URL, path, userinfo or port. */
fun validateApiHost(value: String): String {
    val host = value.lowercase()
    require(host.length <= 253 && Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+qweatherapi\\.com").matches(host)) {
        "QWEATHER_API_HOST must be a dedicated *.qweatherapi.com hostname without scheme or path"
    }
    require(URI("https://$host").host == host)
    return host
}

interface UpstreamAuth { fun header(): Pair<String, String> }

class ApiKeyAuth(private val apiKey: String) : UpstreamAuth {
    init { require(apiKey.length in 8..512 && apiKey.none { it <= ' ' || it == '\u007f' }) { "Invalid API key format" } }
    override fun header() = "X-QW-Api-Key" to apiKey
}

/** Tokens are short-lived, generated locally, cached, and never sent to an Android client. */
class JwtAuth(
    private val privateKey: PrivateKey,
    private val keyId: String,
    private val developerId: String,
    private val projectId: String,
    private val clock: Clock = Clock.systemUTC(),
) : UpstreamAuth {
    private var token: String? = null
    private var refreshAt = 0L

    init {
        require(listOf(keyId, developerId, projectId).all { Regex("[A-Za-z0-9_-]{4,128}").matches(it) }) { "Invalid JWT identifier format" }
    }

    @Synchronized override fun header(): Pair<String, String> {
        val now = clock.instant().epochSecond
        if (token == null || now >= refreshAt) {
            val header = buildJsonObject { put("alg", "EdDSA"); put("kid", keyId) }
            val payload = buildJsonObject { put("iss", developerId); put("sub", projectId); put("iat", now - 30); put("exp", now + 870) }
            val encoder = Base64.getUrlEncoder().withoutPadding()
            fun encode(value: String) = encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
            val input = "${encode(header.toString())}.${encode(payload.toString())}"
            val signature = Signature.getInstance("Ed25519").apply { initSign(privateKey); update(input.toByteArray(Charsets.UTF_8)) }.sign()
            token = "$input.${encoder.encodeToString(signature)}"
            refreshAt = now + 600
        }
        return "Authorization" to "Bearer $token"
    }
}

fun readPrivateKey(path: Path): PrivateKey {
    require(Files.size(path) in 1..8192) { "Invalid Ed25519 private key file size" }
    val pem = Files.readString(path)
    require(pem.contains("-----BEGIN PRIVATE KEY-----")) { "Use a PKCS#8 Ed25519 PRIVATE KEY PEM" }
    val encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
    return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
}
