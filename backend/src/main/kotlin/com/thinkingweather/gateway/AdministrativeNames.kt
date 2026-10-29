package com.thinkingweather.gateway

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Completes known Chinese county names; provider identity and coordinates remain untouched. */
internal object AdministrativeNames {
    private data class Division(val province: String, val parent: String, val name: String)

    private val divisions: List<Division> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/administrative/china-counties.tsv")) {
            "Administrative name resource is missing."
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val parts = line.split('\t')
                check(parts.size == 3) { "Invalid administrative name resource." }
                Division(parts[0], parts[1], parts[2])
            }.toList()
        }
    }

    private val nationalities = listOf(
        "蒙古族", "回族", "藏族", "维吾尔族", "苗族", "彝族", "壮族", "布依族", "朝鲜族", "满族",
        "侗族", "瑶族", "白族", "土家族", "哈尼族", "哈萨克族", "傣族", "黎族", "傈僳族", "佤族",
        "畲族", "高山族", "拉祜族", "水族", "东乡族", "纳西族", "景颇族", "柯尔克孜族", "土族", "达斡尔族",
        "仫佬族", "羌族", "布朗族", "撒拉族", "毛南族", "仡佬族", "锡伯族", "阿昌族", "普米族", "塔吉克族",
        "怒族", "乌孜别克族", "俄罗斯族", "鄂温克族", "德昂族", "保安族", "裕固族", "京族", "塔塔尔族", "独龙族",
        "鄂伦春族", "赫哲族", "门巴族", "珞巴族", "基诺族",
    )
    private val ethnicityPattern = nationalities.joinToString("|") {
        val ethnicName = it.removeSuffix("族")
        // A single character such as 水 belongs to place names too (彭水).
        // Only multi-character ethnicity names have formal spellings omitting 族.
        if (ethnicName.length == 1) it else ethnicName + "族?"
    }
    private val autonomousSuffix = Regex("(?:$ethnicityPattern)+自治(?:县|旗|州)$")
    private val provinceStems = mapOf("内蒙古自治区" to "内蒙古", "广西壮族自治区" to "广西", "宁夏回族自治区" to "宁夏", "新疆维吾尔自治区" to "新疆", "西藏自治区" to "西藏")
    // A bare 州 is part of names such as 杭州/福州/荆州, not a removable suffix.
    private val ordinarySuffixes = listOf("特别行政区", "自治区", "自治县", "自治旗", "自治州", "地区", "林区", "特区", "省", "市", "区", "县", "旗", "盟")

    private fun stem(value: String): String {
        val trimmed = value.trim()
        provinceStems[trimmed]?.let { return it }
        val autonomous = autonomousSuffix.find(trimmed)
        if (autonomous != null) return trimmed.substring(0, autonomous.range.first)
        val suffix = ordinarySuffixes.firstOrNull { trimmed.endsWith(it) }
        return if (suffix == null) trimmed else trimmed.removeSuffix(suffix)
    }

    fun enrich(location: JsonObject): JsonObject {
        val original = location.text("name")?.trim().orEmpty()
        val fullName = complete(original, location.text("adm1").orEmpty(), location.text("adm2").orEmpty(), location.text("country").orEmpty())
        return if (fullName == original || original.isBlank()) location else JsonObject(location + mapOf(
            "name" to JsonPrimitive(fullName), "originalName" to JsonPrimitive(original),
        ))
    }

    internal fun complete(name: String, province: String, parent: String, country: String = "中国"): String {
        if (name.isBlank() || province.isBlank() || country.isNotBlank() && country.lowercase() !in setOf("中国", "china", "cn", "chn")) return name
        val provinceStem = stem(province)
        val provinceDivisions = divisions.filter { stem(it.province) == provinceStem }
        if (provinceDivisions.isEmpty() || name == provinceStem) return name
        // Full-name equality is authoritative. A last character alone is not:
        // 崇州 is a shortened county city and 沙市 is a shortened district.
        if (provinceDivisions.any { it.name == name }) return name
        // Prefecture centres can share a stem with a county (e.g. 岳阳/岳阳县).
        if (provinceDivisions.any { it.parent.isNotBlank() && stem(it.parent) == name }) return name
        val parentStem = stem(parent)
        val matches = provinceDivisions.filter { division ->
            stem(division.name) == name && (parent.isBlank() || parentStem == stem(division.parent) ||
                parentStem == provinceStem || division.parent.isBlank() && parentStem == name)
        }
        // Missing, ambiguous or newer divisions stay as supplied; no guessed “区/县/市”.
        return matches.singleOrNull()?.name ?: name
    }
}
