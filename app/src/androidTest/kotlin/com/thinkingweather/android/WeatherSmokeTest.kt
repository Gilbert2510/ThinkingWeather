package com.thinkingweather.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.longClick
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelStore
import android.app.Application
import com.thinkingweather.android.ui.WeatherApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thinkingweather.android.data.*
import com.thinkingweather.android.data.TestWeather
import com.thinkingweather.android.model.*
import com.thinkingweather.android.model.TemperatureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File

/** Exercises production UI through ordinary dependencies; no network or real preferences are touched. */
@RunWith(AndroidJUnit4::class)
class WeatherSmokeTest {
    @get:Rule
    val compose = createComposeRule()
    private val preferences = MemoryWeatherPreferences()
    private val viewModels = ViewModelStore()
    private lateinit var viewModel: WeatherViewModel
    private val hapticCount = java.util.concurrent.atomic.AtomicInteger()
    private val mapOpenCount = java.util.concurrent.atomic.AtomicInteger()

    @Before fun launchIsolatedUi() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = WeatherViewModel(app, FixtureWeatherSource(), preferences, MemoryCitySummaryCache())
            viewModels.put("weather-ui", viewModel)
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { hapticCount.incrementAndGet() }
            }) {
                WeatherApp(viewModel, onRequestLocation = {}, onOpenWeatherMap = { mapOpenCount.incrementAndGet() })
            }
        }
    }

    @After fun closeIsolatedDependencies() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
    }

    @Test
    fun forecastsAreReadableWithoutRuntimePreview() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithText("示例预览").assertDoesNotExist()
        compose.onNodeWithContentDescription("当前温度 16℃").assertIsDisplayed()
        capture("home-sunny")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("逐小时预报"))
        compose.onNodeWithText("逐小时预报").assertIsDisplayed()
        compose.onNodeWithText("24 小时").assertIsDisplayed()

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("每日预报"))
        compose.onNodeWithText("每日预报").assertIsDisplayed()
        compose.onNodeWithText("7 天").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("日出与日落"))
        compose.onNodeWithTag("sun-path", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("时间均为当地时间 · 太阳轨迹示意"))
        capture("sun-path")
    }

    @Test
    fun searchingAndAddingCityChangesTheDisplayedWeather() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithText("我的城市").assertIsDisplayed()
        capture("cities")
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("shenzhen")

        waitForText("深圳")
        compose.onNodeWithText("深圳").performClick()

        waitForDescription("管理城市，当前 深圳")
        waitForDescription("当前温度 28℃")
        compose.onNodeWithContentDescription("管理城市，当前 深圳").assertIsDisplayed()
        compose.onNodeWithContentDescription("当前温度 28℃").assertIsDisplayed()
        compose.onNodeWithText("示例预览").assertDoesNotExist()
        val persisted = runBlocking(Dispatchers.IO) {
            preferences.read()
        }
        assertEquals((DefaultCities.initial + DefaultCities.shenzhen).map { it.location }, persisted.cities.map { it.location })
    }

    @Test
    fun choosingFahrenheitConvertsTheCurrentTemperature() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("温度单位").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithText("华氏度  °F").performClick()
        compose.onNodeWithContentDescription("返回").performClick()

        waitForDescription("当前温度 61°F")
        compose.onNodeWithContentDescription("当前温度 61°F").assertIsDisplayed()
    }

    @Test
    fun pagesReturnToHomeAndSelectedShanghaiUsesItsOwnWeather() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithText("随 想  /  WEATHER").assertIsDisplayed()
        compose.onNodeWithContentDescription("刷新天气").assertDoesNotExist()
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithText("我的城市").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("shanghai")
        waitForText("上海")
        compose.onNodeWithText("上海").performClick()
        waitForDescription("管理城市，当前 上海")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("温度单位").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        waitForDescription("管理城市，当前 上海")
    }

    @Test
    fun privacyAndAboutAreSeparatePagesAndReturnToSettings() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("操作方式").assertDoesNotExist()
        compose.onNodeWithText("数据模式").assertDoesNotExist()
        compose.onNodeWithText("示例预览").assertDoesNotExist()
        compose.onNodeWithText("隐私与位置").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("隐私"))
        compose.onNodeWithText("隐私").performClick()
        compose.onNodeWithText("位置权限").assertIsDisplayed()
        capture("privacy")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("关于"))
        compose.onNodeWithText("关于").performClick()
        compose.onNodeWithText("随想天气").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("来源与归属"))
        compose.onNodeWithText("来源与归属").assertIsDisplayed()
        capture("about")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        waitForDescription("当前温度 16℃")
    }

    @Test
    fun releasingADownwardDragAtTheTopRefreshesWeather() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithTag("refresh-header").assertDoesNotExist()
        compose.onNodeWithText("松开刷新").assertDoesNotExist()
        val viewModel = this.viewModel
        var previousFetch = 0L
        compose.runOnIdle { previousFetch = viewModel.uiState.value.snapshot!!.fetchedAt }
        compose.onNodeWithTag("weather-scroll").performScrollToNode(hasText("每日预报"))
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            swipeDown(startY = height * .30f, endY = height * .45f, durationMillis = 350)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(previousFetch, viewModel.uiState.value.snapshot!!.fetchedAt) }
        compose.onNodeWithTag("weather-scroll").performScrollToIndex(0)
        compose.onNodeWithTag("refresh-header").assertDoesNotExist()
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            swipeDown(startY = height * .15f, endY = height * .20f, durationMillis = 250)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(previousFetch, viewModel.uiState.value.snapshot!!.fetchedAt) }
        compose.onNodeWithTag("refresh-header").assertDoesNotExist()
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            down(Offset(width / 2f, height * .12f))
            moveBy(Offset(0f, height * .73f), delayMillis = 700)
        }
        compose.onNodeWithText("松开刷新").assertIsDisplayed()
        compose.runOnIdle { assertEquals(previousFetch, viewModel.uiState.value.snapshot!!.fetchedAt) }
        capture("pull-down-ready")
        compose.onNodeWithTag("weather-scroll").performTouchInput { up() }
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.snapshot?.fetchedAt?.let { it > previousFetch } == true &&
                compose.onAllNodesWithText("松开刷新").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("正在刷新天气").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("refresh-header").assertDoesNotExist()
    }

    @Test
    fun stormSnowAndNightScenariosCanBeBrowsedFromTheCityPage() {
        waitForDescription("当前温度 16℃")
        val viewModel = this.viewModel
        for ((cityName, filename) in listOf("厦门" to "home-thunderstorm", "哈尔滨" to "home-snow", "天津" to "home-night")) {
            val scenario = TestWeather.catalog.first { it.city.name == cityName }
            compose.onNodeWithContentDescription("管理城市").performClick()
            compose.onNodeWithContentDescription("搜索城市").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(cityName)
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText(cityName) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasText(cityName) and !hasSetTextAction()).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                viewModel.uiState.value.snapshot?.let {
                    it.city.location == scenario.city.location && it.now.text == scenario.text
                } == true
            }
            waitForDescription("管理城市，当前 $cityName")
            capture(filename)
        }
    }

    @Test
    fun toolbarStaysVisibleAndHourlyDetailsOpenAtTheRequestedSection() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithTag("weather-scroll").performScrollToNode(hasText("空气质量"))
        compose.onNodeWithTag("home-toolbar").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        capture("sticky-toolbar")
        compose.onNodeWithTag("weather-scroll").performScrollToNode(hasText("逐小时预报"))
        compose.onNodeWithText("逐小时预报").performClick()
        waitForText("天气详情")
        compose.onNodeWithTag("insight-hourly").assertIsDisplayed()
        capture("hourly-details")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("home-toolbar").assertIsDisplayed()
    }

    @Test
    fun locationMessageCollapsesAfterThreeSeconds() {
        waitForDescription("当前温度 16℃")
        val viewModel = this.viewModel
        compose.runOnIdle { viewModel.reportLocationError("已定位到北京") }
        waitForText("已定位到北京")
        compose.onNodeWithTag("location-message").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_400)
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 6_000) {
            compose.onAllNodesWithText("已定位到北京").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("location-message").assertDoesNotExist()
    }

    @Test
    fun dedicatedCitySearchReturnsToTheCityManager() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithContentDescription("搜索城市").performClick()
        waitForText("搜索城市")
        compose.onNodeWithText("国内").assertIsDisplayed()
        capture("city-search")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我的城市").assertIsDisplayed()
    }

    @Test
    fun cityEditorReordersAndDeletesABatchWhileKeepingOneCity() {
        waitForDescription("当前温度 16℃")
        val viewModel = this.viewModel
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithContentDescription("定位城市", substring = true).assertIsDisplayed()
        compose.onNodeWithText("编辑").performClick()
        val first = compose.onNodeWithTag("city-card-${DefaultCities.beijing.location}").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("city-card-${DefaultCities.shanghai.location}").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("city-drag-${DefaultCities.beijing.location}", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(650)
            moveBy(Offset(0f, second.center.y - first.center.y), delayMillis = 250)
            advanceEventTime(100)
        }
        capture("city-drag-held")
        compose.onNodeWithTag("city-drag-${DefaultCities.beijing.location}", useUnmergedTree = true).performTouchInput { up() }
        capture("city-edit")
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.cities.first().location == DefaultCities.shanghai.location }
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选择3项").assertIsDisplayed()
        compose.onNodeWithText("删除（3）").performClick()
        compose.onNodeWithText("知道了").performClick()
        compose.runOnIdle { assertEquals(3, viewModel.uiState.value.cities.size) }
        compose.onNodeWithText("取消全选").performClick()
        compose.onNodeWithContentDescription("选择 上海").performClick()
        compose.onNodeWithContentDescription("选择 杭州").performClick()
        compose.onNodeWithText("删除（2）").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.cities.size == 1 }
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(DefaultCities.beijing.location, viewModel.uiState.value.cities.single().location) }
    }

    @Test
    fun cityLongPressSelectsOnceWithHapticsAndKeepsTheListInPlace() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithText("使用当前位置").assertDoesNotExist()
        compose.onNodeWithText("已保存").assertDoesNotExist()
        val card = compose.onNodeWithTag("city-card-${DefaultCities.shanghai.location}")
        val before = card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput { longClick() }
        compose.onNodeWithText("已选择1项").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择 上海").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, hapticCount.get()) }
        val after = card.fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.height, after.height, 1f)
        capture("city-long-press")
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("选择项目").assertIsDisplayed()
    }

    @Test
    fun cityCardPressShrinksAndTapOpensItsWeatherThroughContainerTransition() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("管理城市").performClick()
        val card = compose.onNodeWithTag("city-card-${DefaultCities.shanghai.location}")
        val original = card.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        val pressed = card.fetchSemanticsNode().boundsInRoot
        assertTrue("Press should visibly compress the card", pressed.width < original.width * .99f)
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(160)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "validation").apply { mkdirs() }
        File(directory, "city-expand-midpoint.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        compose.mainClock.autoAdvance = true
        waitForDescription("管理城市，当前 上海")
        compose.onNodeWithContentDescription("当前温度 21℃").assertIsDisplayed()
    }

    @Test
    fun weatherMapEntryRequestsItsWindowWithoutChangingTheSelectedCity() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("天气地图").performClick()
        compose.runOnIdle { assertEquals(1, mapOpenCount.get()) }
        compose.onNodeWithContentDescription("当前温度 16℃").assertIsDisplayed()
    }

    @Test
    fun detailTabsShowDailyAirUvSunAndLifeData() {
        waitForDescription("当前温度 16℃")
        compose.onNodeWithContentDescription("当前温度 16℃").performClick()
        waitForText("天气详情")
        for (section in listOf("daily", "air", "uv", "sun", "life", "indices")) {
            compose.onNodeWithTag("insight-tab-$section").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 6_000) {
                compose.onAllNodes(hasTestTag("insight-$section")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithTag("insight-$section").assertIsDisplayed()
            capture("details-$section")
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun waitForDescription(description: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}


/** In-memory persistence keeps tests deterministic and leaves the installed app's data untouched. */
private class MemoryWeatherPreferences : WeatherPreferences {
    @Volatile private var value = SavedWeatherState(DefaultCities.initial, DefaultCities.beijing,
        TemperatureUnit.CELSIUS, DefaultCities.beijing.location)
    private val weather = java.util.concurrent.ConcurrentHashMap<String, WeatherSnapshot>()
    override suspend fun read() = value
    override suspend fun setCities(cities: List<City>, selected: City, locatedCityLocation: String?) {
        value = value.copy(cities = cities, selectedCity = selected, locatedCityLocation = locatedCityLocation)
    }
    override suspend fun setUnit(unit: TemperatureUnit) { value = value.copy(unit = unit) }
    override suspend fun cached(city: City) = weather[city.location]
    override suspend fun cachedAll() = weather.values.toList()
    override suspend fun save(snapshot: WeatherSnapshot) { weather[snapshot.city.location] = snapshot }
}

private class MemoryCitySummaryCache : CitySummaryCache {
    @Volatile private var cards = emptyList<CityWeatherSummary>()
    override suspend fun read() = cards
    override suspend fun save(cards: Collection<CityWeatherSummary>) { this.cards = cards.toList() }
}

private class FixtureWeatherSource : WeatherDataSource {
    override val networkUnavailable = false
    override suspend fun weather(city: City) = TestWeather.create(city)
    override suspend fun currentSummary(city: City) = CityWeatherSummary.from(TestWeather.create(city))
    override suspend fun search(query: String): List<City> {
        val aliases = mapOf("beijing" to "北京", "shanghai" to "上海", "hangzhou" to "杭州",
            "shenzhen" to "深圳", "xiamen" to "厦门", "haerbin" to "哈尔滨", "tianjin" to "天津")
        val normalized = aliases[query.lowercase()] ?: query
        return TestWeather.catalog.map { it.city }.filter { it.name.contains(normalized) }
    }
    override fun close() = Unit
}
