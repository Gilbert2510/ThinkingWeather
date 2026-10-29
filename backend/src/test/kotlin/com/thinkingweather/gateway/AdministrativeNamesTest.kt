package com.thinkingweather.gateway

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AdministrativeNamesTest {
    @Test fun `district county and county city keep their actual administrative suffix`() {
        assertEquals("沙坪坝区", AdministrativeNames.complete("沙坪坝", "重庆市", "重庆"))
        assertEquals("东城区", AdministrativeNames.complete("东城", "北京市", "北京"))
        assertEquals("正定县", AdministrativeNames.complete("正定", "河北省", "石家庄"))
        assertEquals("江阴市", AdministrativeNames.complete("江阴", "江苏省", "无锡"))
        assertEquals("崇州市", AdministrativeNames.complete("崇州", "四川省", "成都"))
        assertEquals("沙市区", AdministrativeNames.complete("沙市", "湖北省", "荆州"))
        assertEquals("西湖区", AdministrativeNames.complete("西湖", "浙江省", "杭州"))
        assertEquals("鼓楼区", AdministrativeNames.complete("鼓楼", "福建省", "福州"))
        assertEquals("南明区", AdministrativeNames.complete("南明", "贵州", "贵阳"))
        assertEquals("彭水苗族土家族自治县", AdministrativeNames.complete("彭水", "重庆市", "重庆"))
        assertEquals("三都水族自治县", AdministrativeNames.complete("三都", "贵州省", "黔南"))
    }

    @Test fun `qualified names and prefecture centres are never given guessed county suffixes`() {
        assertEquals("沙坪坝区", AdministrativeNames.complete("沙坪坝区", "重庆市", "重庆"))
        assertEquals("岳阳", AdministrativeNames.complete("岳阳", "湖南省", "岳阳"))
        assertEquals("北京", AdministrativeNames.complete("北京", "北京市", "北京"))
        assertEquals("新行政地名", AdministrativeNames.complete("新行政地名", "河北省", "石家庄"))
    }

    @Test fun `ambiguous missing or mismatched hierarchy stays exactly as supplied`() {
        assertEquals("市中", AdministrativeNames.complete("市中", "山东省", ""))
        assertEquals("沙坪坝", AdministrativeNames.complete("沙坪坝", "重庆市", "成都"))
        assertEquals("东城", AdministrativeNames.complete("东城", "", "北京"))
        assertEquals("东城", AdministrativeNames.complete("东城", "北京市", "北京", "Japan"))
    }

    @Test fun `completing a display name does not replace the provider location identity`() {
        val original = Json.parseToJsonElement("""{"name":"沙坪坝","id":"101040106","lat":"29.57","lon":"106.45","adm1":"重庆市","adm2":"重庆","country":"中国","type":"city"}""").jsonObject
        val result = AdministrativeNames.enrich(original)
        assertEquals("沙坪坝区", result.text("name"))
        assertEquals("沙坪坝", result.text("originalName"))
        original.filterKeys { it != "name" }.forEach { (key, value) -> assertEquals(value, result[key]) }
        assertEquals("沙坪坝", original.text("name"))
    }
}
