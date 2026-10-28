package com.thinkingweather.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thinkingweather.android.data.WeatherStore
import com.thinkingweather.android.data.DemoWeather
import com.thinkingweather.android.model.DefaultCities
import com.thinkingweather.android.model.TemperatureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File

/** Exercises the actual activity and UI, using explicitly selected local sample weather. */
@RunWith(AndroidJUnit4::class)
class WeatherSmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    // Configure before launching the activity: these tests never depend on a gateway or location.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DemoFixtureRule()).around(compose)

    @Test
    fun sampleWeatherIsClearlyLabelledAndForecastsAreReadable() {
        waitForDescription("当前温度 16°C")
        compose.onNodeWithText("示例预览 · 非实时天气").assertIsDisplayed()
        compose.onNodeWithContentDescription("当前温度 16°C").assertIsDisplayed()
        capture("home-sunny")

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("逐小时预报"))
        compose.onNodeWithText("逐小时预报").assertIsDisplayed()
        compose.onNodeWithText("24 小时").assertIsDisplayed()

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("每日预报"))
        compose.onNodeWithText("每日预报").assertIsDisplayed()
        compose.onNodeWithText("7 天").assertIsDisplayed()
    }

    @Test
    fun searchingAndAddingCityChangesTheDisplayedWeather() {
        waitForDescription("当前温度 16°C")
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithText("我的城市").assertIsDisplayed()
        capture("cities")
        compose.onNode(hasSetTextAction()).performTextInput("shenzhen")

        waitForText("深圳")
        compose.onNodeWithText("深圳").performClick()

        waitForDescription("管理城市，当前 深圳")
        waitForDescription("当前温度 28°C")
        compose.onNodeWithContentDescription("管理城市，当前 深圳").assertIsDisplayed()
        compose.onNodeWithContentDescription("当前温度 28°C").assertIsDisplayed()
        compose.onNodeWithText("示例预览 · 非实时天气").assertIsDisplayed()
        val persisted = runBlocking(Dispatchers.IO) {
            WeatherStore(compose.activity, true).read()
        }
        assertEquals(DefaultCities.initial.map { it.location }, persisted.cities.map { it.location })
    }

    @Test
    fun choosingFahrenheitConvertsTheCurrentTemperature() {
        waitForDescription("当前温度 16°C")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("温度单位").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithText("华氏度  °F").performClick()
        compose.onNodeWithContentDescription("返回").performClick()

        waitForDescription("当前温度 61°F")
        compose.onNodeWithContentDescription("当前温度 61°F").assertIsDisplayed()
    }

    @Test
    fun pagesReturnToHomeAndPreviewIncludesOvercastShanghai() {
        waitForDescription("当前温度 16°C")
        compose.onNodeWithText("随 想  /  WEATHER").assertIsDisplayed()
        compose.onNodeWithContentDescription("刷新天气").assertDoesNotExist()
        compose.onNodeWithContentDescription("管理城市").performClick()
        compose.onNodeWithText("我的城市").assertIsDisplayed()
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
    fun releasingADownwardDragAtTheTopRefreshesWeather() {
        waitForDescription("当前温度 16°C")
        val viewModel = ViewModelProvider(compose.activity)[WeatherViewModel::class.java]
        var previousFetch = 0L
        compose.runOnIdle { previousFetch = viewModel.uiState.value.snapshot!!.fetchedAt }
        compose.onNodeWithTag("weather-scroll").performScrollToNode(hasText("每日预报"))
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            swipeDown(startY = height * .30f, endY = height * .45f, durationMillis = 350)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(previousFetch, viewModel.uiState.value.snapshot!!.fetchedAt) }
        compose.onNodeWithTag("weather-scroll").performScrollToNode(hasTestTag("refresh-header"))
        compose.onNodeWithText("下拉刷新天气").assertIsDisplayed()
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            swipeDown(startY = height * .15f, endY = height * .20f, durationMillis = 250)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(previousFetch, viewModel.uiState.value.snapshot!!.fetchedAt) }
        compose.onNodeWithTag("weather-scroll").performTouchInput {
            swipeDown(startY = height * .12f, endY = height * .85f, durationMillis = 700)
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.snapshot?.fetchedAt?.let { it > previousFetch } == true
        }
        waitForText("下拉刷新天气")
        compose.onNodeWithText("下拉刷新天气").assertIsDisplayed()
    }

    @Test
    fun stormSnowAndNightScenariosCanBeBrowsedFromTheCityPage() {
        waitForDescription("当前温度 16°C")
        val viewModel = ViewModelProvider(compose.activity)[WeatherViewModel::class.java]
        for ((cityName, filename) in listOf("厦门" to "home-thunderstorm", "哈尔滨" to "home-snow", "天津" to "home-night")) {
            val scenario = DemoWeather.catalog.first { it.city.name == cityName }
            compose.onNodeWithContentDescription("管理城市").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(cityName)
            waitForText(cityName)
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

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        val directory = File(compose.activity.getExternalFilesDir(null), "validation").apply { mkdirs() }
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

private class DemoFixtureRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = WeatherStore(context, BuildConfig.WEATHER_BASE_URL.isNotBlank())
            val original = runBlocking(Dispatchers.IO) { store.read() }
            try {
                runBlocking(Dispatchers.IO) {
                    store.setDemo(true)
                    store.setUnit(TemperatureUnit.CELSIUS)
                    store.setCities(DefaultCities.initial, DefaultCities.beijing)
                }
                base.evaluate()
            } finally {
                runBlocking(Dispatchers.IO) {
                    store.setDemo(original.isDemo)
                    store.setUnit(original.unit)
                    store.setCities(original.cities, original.selectedCity)
                }
            }
        }
    }
}
