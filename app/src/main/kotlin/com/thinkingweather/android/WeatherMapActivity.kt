package com.thinkingweather.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri
import com.thinkingweather.android.network.OFFICIAL_WEATHER_MAP
import com.thinkingweather.android.network.isExternalHttpsUrl
import com.thinkingweather.android.network.isOfficialMapUrl
import com.thinkingweather.android.ui.Glyph
import com.thinkingweather.android.ui.LineIcon
import com.thinkingweather.android.ui.ThinkingWeatherTheme
import com.thinkingweather.android.ui.WeatherIconButton

/** A native WebView window: the website gets a stable viewport outside Compose shared overlays. */
class WeatherMapActivity : ComponentActivity() {
    private var browser: WebView? = null
    private lateinit var mapContainer: FrameLayout
    private lateinit var errorView: ComposeView
    private lateinit var progress: ProgressBar
    private var failure by mutableStateOf<String?>(null)
    private var browserFailure by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (dark) 0xFF102431.toInt() else 0xFFF5F8FA.toInt())
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        root.addView(ComposeView(this).apply { setContent {
            ThinkingWeatherTheme { Surface {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeatherIconButton("返回", Glyph.Back, ::finish, MaterialTheme.colorScheme.onSurface)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text("天气地图", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text("和风官方 · map.qweather.com", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        IconButton(onClick = { openBrowser(OFFICIAL_WEATHER_MAP) },
                            modifier = Modifier.semantics { contentDescription = "在浏览器打开" }) {
                            LineIcon(Glyph.External, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (browserFailure) Text("未找到可用浏览器", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp))
                }
            } }
        } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * resources.displayMetrics.density).toInt()))
        mapContainer = FrameLayout(this)
        root.addView(mapContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        errorView = ComposeView(this).apply {
            visibility = View.GONE
            setContent { ThinkingWeatherTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    LineIcon(Glyph.Map, Modifier.size(36.dp), MaterialTheme.colorScheme.primary)
                    Text(failure.orEmpty(), modifier = Modifier.padding(vertical = 18.dp), fontSize = 14.sp)
                    Button(onClick = ::loadMap) { Text("重新加载") }
                    TextButton(onClick = { openBrowser(OFFICIAL_WEATHER_MAP) }) { Text("在浏览器中查看") }
                }
            } } }
        }
        setContentView(root)
        // Load after the native window has a real size, rather than a transient animation viewport.
        mapContainer.post { if (!isFinishing && !isDestroyed) loadMap() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadMap() {
        releaseBrowser()
        failure = null; errorView.visibility = View.GONE
        progress.progress = 0; progress.visibility = View.VISIBLE
        val view = WebView(this)
        browser = view
        view.settings.apply {
            javaScriptEnabled = true // Required by the provider's interactive map.
            domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100 || failure != null) View.GONE else View.VISIBLE
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, false, false)
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (isOfficialMapUrl(target)) return false
                if (request.isForMainFrame && request.hasGesture()) openBrowser(target)
                return true
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError("地图暂时无法加载，请检查网络后重试。")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showError("地图服务暂时不可用，请稍后重试。")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel(); showError("地图的安全连接暂不可用，请稍后重试。")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                releaseBrowser(); showError("地图已停止运行，请重新加载。")
                return true
            }
        }
        mapContainer.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Keep the error surface above the native browser without wrapping either in a Compose layer.
        (errorView.parent as? ViewGroup)?.removeView(errorView)
        mapContainer.addView(errorView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.loadUrl(OFFICIAL_WEATHER_MAP)
    }

    private fun showError(message: String) {
        failure = message; progress.visibility = View.GONE; errorView.visibility = View.VISIBLE
    }

    private fun openBrowser(url: String) {
        if (isExternalHttpsUrl(url)) browserFailure = runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isFailure
    }

    private fun releaseBrowser() {
        browser?.let { view -> mapContainer.removeView(view); view.stopLoading(); view.webChromeClient = null; view.destroy() }
        browser = null
    }

    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onDestroy() { releaseBrowser(); super.onDestroy() }
}
