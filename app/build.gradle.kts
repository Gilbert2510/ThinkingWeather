import java.util.Properties
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionCode = appVersion.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: error("VERSION_CODE must be a positive integer in version.properties.")
require(appVersionCode > 0) { "VERSION_CODE must be positive." }
val appVersionName = appVersion.getProperty("VERSION_NAME").orEmpty()
group = "com.thinkingweather"
version = appVersionName
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?").matches(appVersionName)) {
    "VERSION_NAME must use major.minor.patch in version.properties."
}

val releaseKeys = Properties().apply {
    rootProject.file("signing/keystore.properties").takeIf { it.isFile }
        ?.inputStream()?.use { load(it) }
}
fun signingValue(environment: String, property: String): String =
    providers.environmentVariable(environment).orNull ?: releaseKeys.getProperty(property).orEmpty()
val releaseStorePath = signingValue("WEATHER_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("WEATHER_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("WEATHER_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("WEATHER_KEY_PASSWORD", "keyPassword")
val signingValues = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = signingValues.all { it.isNotBlank() }
require(signingValues.none { it.isNotBlank() } || releaseSigningConfigured) {
    "Release signing is incomplete. Supply all four WEATHER_* signing variables or signing/keystore.properties."
}
val releaseStore = releaseStorePath.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
require(!releaseSigningConfigured || releaseStore?.isFile == true) {
    "The configured release keystore does not exist. Restore it from your private backup."
}

val localConfiguration = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val weatherBaseUrl = providers.gradleProperty("WEATHER_BASE_URL")
    .orElse(providers.environmentVariable("WEATHER_BASE_URL"))
    .orElse(localConfiguration.getProperty("WEATHER_BASE_URL", ""))
    .get().trim().trimEnd('/')
require(weatherBaseUrl.isEmpty() || runCatching {
    val uri = URI(weatherBaseUrl)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.query == null && uri.fragment == null
}.getOrDefault(false)) { "WEATHER_BASE_URL must be an HTTPS URL without credentials, query or fragment." }
val buildConfigUrl = "\"" + weatherBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val debugWeatherBaseUrl = providers.gradleProperty("WEATHER_DEBUG_BASE_URL")
    .orElse(providers.environmentVariable("WEATHER_DEBUG_BASE_URL"))
    .orElse(localConfiguration.getProperty("WEATHER_DEBUG_BASE_URL", weatherBaseUrl))
    .get().trim().trimEnd('/')
require(debugWeatherBaseUrl.isEmpty() || runCatching {
    val uri = URI(debugWeatherBaseUrl)
    (uri.scheme == "https" || (uri.scheme == "http" &&
        uri.host.removeSurrounding("[", "]") in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"))) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
}.getOrDefault(false)) { "WEATHER_DEBUG_BASE_URL requires HTTPS, or HTTP on localhost/127.0.0.1/[::1]/10.0.2.2." }

android {
    namespace = "com.thinkingweather.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.thinkingweather.android"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WEATHER_BASE_URL", buildConfigUrl)
    }
    buildFeatures { compose = true; buildConfig = true }
    if (releaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = releaseStore
            storeType = releaseKeys.getProperty("storeType", "PKCS12")
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "WEATHER_BASE_URL", "\"$debugWeatherBaseUrl\"")
        }
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Require private release signing credentials without generating or replacing keys."
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is not configured. See docs/RELEASING.md. Never use the debug key for releases."
        }
    }
}
tasks.register("assembleSignedRelease") {
    group = "build"
    description = "Build a release APK using the configured private signing certificate."
    dependsOn(verifyReleaseSigning, "assembleRelease")
}
tasks.register("bundleSignedRelease") {
    group = "build"
    description = "Build a signed release AAB for a store submission."
    dependsOn(verifyReleaseSigning, "bundleRelease")
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    mustRunAfter(verifyReleaseSigning)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // Compose's transitive Espresso 3.5 still reflects a removed InputManager API on Android 17.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
