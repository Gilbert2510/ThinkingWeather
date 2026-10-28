package com.thinkingweather.gateway

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder

data class Coordinates(val longitude: String, val latitude: String) {
    val location get() = "$longitude,$latitude"
    val path get() = "$latitude/$longitude"
}

object Validation {
    private val locationId = Regex("[A-Za-z0-9]{4,32}")
    private val decimal = Regex("-?\\d{1,3}(?:\\.\\d{1,8})?")

    fun location(raw: String): String {
        val input = raw.trim()
        if (',' in input) return coordinates(input).location
        if (!locationId.matches(input)) throw GatewayException(400, "invalid_location")
        return input
    }

    /** Round to two decimals: QWeather GeoAPI precision, less location retention and cache fragmentation. */
    fun coordinates(raw: String): Coordinates {
        val pieces = raw.split(',')
        if (pieces.size != 2 || pieces.any { !decimal.matches(it) }) throw GatewayException(400, "invalid_coordinates")
        val lon = pieces[0].toBigDecimal()
        val lat = pieces[1].toBigDecimal()
        if (lon.abs() > BigDecimal(180) || lat.abs() > BigDecimal(90)) throw GatewayException(400, "invalid_coordinates")
        fun rounded(number: BigDecimal) = number.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return Coordinates(rounded(lon), rounded(lat))
    }

    fun query(raw: String): String {
        val query = raw.trim()
        if (query.length !in 1..80 || query.any { it.isISOControl() || it in "<>&?#/\\" }) throw GatewayException(400, "invalid_query")
        return query
    }

    fun parameters(rawQuery: String?, expected: String): String {
        if (rawQuery == null || rawQuery.length > 512) throw GatewayException(400, "invalid_query")
        val pairs = try {
            rawQuery.split('&').map { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) throw IllegalArgumentException()
                URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8)
            }
        } catch (_: IllegalArgumentException) { throw GatewayException(400, "invalid_query") }
        if (pairs.size != 1 || pairs[0].first != expected) throw GatewayException(400, "invalid_query")
        return pairs[0].second
    }
}
