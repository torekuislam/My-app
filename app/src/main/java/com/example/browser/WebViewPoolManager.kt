package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.preferences.AppSettings
import com.example.domain.CookieParseResult
import com.example.domain.CookieParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BrowserTabRuntimeState(
    val profileId: Long,
    val profileKey: String,
    val currentUrl: String,
    val pageTitle: String,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

class WebViewPoolManager(
    private val context: Context,
    private val onTabStateChanged: (BrowserTabRuntimeState) -> Unit,
    private val onPageCommitted: (profileId: Long, url: String, title: String) -> Unit,
    private val onCookiesObserved: (profileId: Long, profileKey: String, rawCookies: String) -> Unit,
) {
    private val webViews = LinkedHashMap<String, WebView>()
    private val savedBundles = mutableMapOf<String, Bundle>()
    private val fallbackCookieCache = mutableMapOf<String, String>()
    private var activeFallbackKey: String? = null

    val isMultiProfileSupported: Boolean by lazy {
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
            .getOrDefault(false)
    }

    private val facebookTargets = listOf(
        "https://www.facebook.com",
        "https://facebook.com",
        "https://m.facebook.com",
        "https://mbasic.facebook.com",
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        profileId: Long,
        profileKey: String,
        initialUrl: String,
        customUserAgent: String?,
        settings: AppSettings,
        fallbackCookies: String?,
    ): WebView {
        if (!isMultiProfileSupported && activeFallbackKey != profileKey) {
            swapFallbackProfileCookies(profileKey, fallbackCookies)
        }

        val existing = webViews[profileKey]
        if (existing != null) {
            applyDesktopConfiguration(existing, customUserAgent, settings)
            return existing
        }

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        if (isMultiProfileSupported) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(profileKey)
                WebViewCompat.setProfile(webView, profileKey)
            }
        }

        applyDesktopConfiguration(webView, customUserAgent, settings)
        val cookieManager = getCookieManagerForProfile(profileKey)
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val url = view?.url ?: initialUrl
                val title = view?.title ?: "Facebook"
                onTabStateChanged(
                    BrowserTabRuntimeState(
                        profileId = profileId,
                        profileKey = profileKey,
                        currentUrl = url,
                        pageTitle = title,
                        progress = newProgress,
                        isLoading = newProgress in 1..99,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                )
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                val safeTitle = title?.takeIf { it.isNotBlank() } ?: return
                val url = view?.url ?: initialUrl
                onTabStateChanged(
                    BrowserTabRuntimeState(
                        profileId = profileId,
                        profileKey = profileKey,
                        currentUrl = url,
                        pageTitle = safeTitle,
                        progress = view?.progress ?: 100,
                        isLoading = (view?.progress ?: 100) < 100,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                )
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme != "http" && scheme != "https") {
                    // Block intent:// or fb:// external app hijacking so login stays inside the profile browser
                    return true
                }
                if (settings.enforceDesktopFacebookUrl && request.isForMainFrame) {
                    val desktopUri = rewriteToDesktopFacebook(uri)
                    if (desktopUri != null) {
                        view?.loadUrl(desktopUri)
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val safeUrl = url ?: initialUrl
                onTabStateChanged(
                    BrowserTabRuntimeState(
                        profileId = profileId,
                        profileKey = profileKey,
                        currentUrl = safeUrl,
                        pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Loading...",
                        progress = 15,
                        isLoading = true,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val safeUrl = url ?: initialUrl
                val safeTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Facebook"
                if (view != null) {
                    injectDesktopViewport(view, settings.desktopViewportWidth)
                }
                getCookieManagerForProfile(profileKey).flush()

                val liveCookies = runCatching {
                    getCookieManagerForProfile(profileKey).getCookie("https://www.facebook.com")
                }.getOrNull().orEmpty()
                if (liveCookies.contains("c_user=")) {
                    fallbackCookieCache[profileKey] = liveCookies
                    onCookiesObserved(profileId, profileKey, liveCookies)
                }

                onPageCommitted(profileId, safeUrl, safeTitle)
                onTabStateChanged(
                    BrowserTabRuntimeState(
                        profileId = profileId,
                        profileKey = profileKey,
                        currentUrl = safeUrl,
                        pageTitle = safeTitle,
                        progress = 100,
                        isLoading = false,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false,
                    )
                )
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                // Recover gracefully if OS killed background renderer under heavy multi-tab load
                val lastUrl = view?.url ?: initialUrl
                webViews.remove(profileKey)?.let { dead ->
                    (dead.parent as? ViewGroup)?.removeView(dead)
                    dead.destroy()
                }
                onTabStateChanged(
                    BrowserTabRuntimeState(
                        profileId = profileId,
                        profileKey = profileKey,
                        currentUrl = lastUrl,
                        pageTitle = "Tab suspended — tap reload",
                        progress = 0,
                        isLoading = false,
                        canGoBack = false,
                        canGoForward = false,
                    )
                )
                return true
            }
        }

        val bundle = savedBundles.remove(profileKey)
        if (bundle != null) {
            webView.restoreState(bundle)
        } else {
            val targetUrl = if (settings.enforceDesktopFacebookUrl) {
                rewriteToDesktopFacebook(Uri.parse(initialUrl)) ?: initialUrl
            } else {
                initialUrl
            }
            webView.loadUrl(targetUrl)
        }

        webViews[profileKey] = webView
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applyDesktopConfiguration(
        webView: WebView,
        customUserAgent: String?,
        settings: AppSettings,
    ) {
        val ua = customUserAgent?.takeIf { it.isNotBlank() } ?: settings.defaultUserAgent
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = ua
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching {
                val majorVersion = Regex("""Chrome/(\d+)""").find(ua)?.groupValues?.getOrNull(1) ?: "135"
                val metadata = UserAgentMetadata.Builder()
                    .setBrandVersionList(
                        listOf(
                            UserAgentMetadata.BrandVersion.Builder()
                                .setBrand("Google Chrome")
                                .setMajorVersion(majorVersion)
                                .setFullVersion("$majorVersion.0.0.0")
                                .build(),
                            UserAgentMetadata.BrandVersion.Builder()
                                .setBrand("Chromium")
                                .setMajorVersion(majorVersion)
                                .setFullVersion("$majorVersion.0.0.0")
                                .build(),
                        )
                    )
                    .setPlatform("Windows")
                    .setPlatformVersion("10.0.0")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setMobile(false)
                    .setModel("")
                    .build()
                WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata)
            }
        }
    }

    fun refreshSettingsAcrossOpenTabs(settings: AppSettings) {
        webViews.values.forEach { wv ->
            applyDesktopConfiguration(wv, null, settings)
        }
    }

    suspend fun importCookiesIntoProfile(
        profileKey: String,
        rawCookieInput: String,
        reloadIfOpen: Boolean = true,
    ): CookieParseResult = withContext(Dispatchers.Main) {
        val parsed = CookieParser.parse(rawCookieInput)
        if (parsed.cookies.isEmpty()) return@withContext parsed

        fallbackCookieCache[profileKey] = parsed.normalizedCookieString

        if (isMultiProfileSupported || activeFallbackKey == profileKey || activeFallbackKey == null) {
            val cm = getCookieManagerForProfile(profileKey)
            cm.setAcceptCookie(true)
            clearCookiesForManager(cm)
            for (cookie in parsed.cookies) {
                val header = CookieParser.toSetCookieHeader(cookie)
                for (target in facebookTargets) {
                    val d = CompletableDeferred<Boolean>()
                    cm.setCookie(target, header) { ok -> d.complete(ok) }
                    d.await()
                }
            }
            cm.flush()
            activeFallbackKey = profileKey
        }

        if (reloadIfOpen) {
            webViews[profileKey]?.let { wv ->
                val current = wv.url.orEmpty()
                if (current.contains("facebook.com", ignoreCase = true)) {
                    wv.loadUrl("https://www.facebook.com/")
                } else {
                    wv.reload()
                }
            }
        }
        parsed
    }

    suspend fun exportLiveCookies(profileKey: String, fallbackSnapshot: String): String =
        withContext(Dispatchers.Main) {
            val live = if (isMultiProfileSupported || activeFallbackKey == profileKey) {
                runCatching {
                    getCookieManagerForProfile(profileKey).getCookie("https://www.facebook.com")
                }.getOrNull().orEmpty()
            } else {
                ""
            }
            live.ifBlank { fallbackCookieCache[profileKey].orEmpty() }.ifBlank { fallbackSnapshot }
        }

    fun autoFillCredentials(profileKey: String, email: String, password: String) {
        val wv = webViews[profileKey] ?: return
        val escapedEmail = escapeJs(email)
        val escapedPass = escapeJs(password)
        val js = """
            (function() {
                function setNativeValue(el, value) {
                    if (!el || !value) return false;
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    setter.call(el, value);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return true;
                }
                var emailEl = document.querySelector('input[name="email"], #email, input[type="email"]');
                var passEl = document.querySelector('input[name="pass"], #pass, input[type="password"]');
                setNativeValue(emailEl, "$escapedEmail");
                setNativeValue(passEl, "$escapedPass");
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    fun navigate(profileKey: String, rawInput: String, enforceDesktopFb: Boolean) {
        val wv = webViews[profileKey] ?: return
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val url = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
        val finalUrl = if (enforceDesktopFb) {
            rewriteToDesktopFacebook(Uri.parse(url)) ?: url
        } else {
            url
        }
        wv.loadUrl(finalUrl)
    }

    fun goBack(profileKey: String) {
        webViews[profileKey]?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward(profileKey: String) {
        webViews[profileKey]?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload(profileKey: String) {
        webViews[profileKey]?.reload()
    }

    fun closeTab(profileKey: String) {
        webViews.remove(profileKey)?.let { wv ->
            val bundle = Bundle()
            wv.saveState(bundle)
            savedBundles[profileKey] = bundle
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    fun destroyProfileStorage(profileKey: String) {
        closeTab(profileKey)
        savedBundles.remove(profileKey)
        fallbackCookieCache.remove(profileKey)
        if (isMultiProfileSupported) {
            runCatching {
                ProfileStore.getInstance().deleteProfile(profileKey)
            }
        }
    }

    fun destroyAll() {
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViews.clear()
        savedBundles.clear()
    }

    private fun getCookieManagerForProfile(profileKey: String): CookieManager {
        if (isMultiProfileSupported) {
            val profile: Profile? = runCatching {
                ProfileStore.getInstance().getOrCreateProfile(profileKey)
            }.getOrNull()
            if (profile != null) return profile.cookieManager
        }
        return CookieManager.getInstance()
    }

    private fun swapFallbackProfileCookies(incomingKey: String, storedSnapshot: String?) {
        val cm = CookieManager.getInstance()
        activeFallbackKey?.let { prevKey ->
            val currentLive = runCatching { cm.getCookie("https://www.facebook.com") }.getOrNull().orEmpty()
            if (currentLive.isNotBlank()) {
                fallbackCookieCache[prevKey] = currentLive
            }
        }
        cm.removeAllCookies(null)
        cm.flush()

        val targetCookies = fallbackCookieCache[incomingKey]?.takeIf { it.isNotBlank() }
            ?: storedSnapshot.orEmpty()
        if (targetCookies.isNotBlank()) {
            val parsed = CookieParser.parse(targetCookies)
            for (cookie in parsed.cookies) {
                val header = CookieParser.toSetCookieHeader(cookie)
                for (target in facebookTargets) {
                    cm.setCookie(target, header)
                }
            }
            cm.flush()
        }
        activeFallbackKey = incomingKey
    }

    private suspend fun clearCookiesForManager(cm: CookieManager) {
        val deferred = CompletableDeferred<Boolean>()
        cm.removeAllCookies { removed -> deferred.complete(removed) }
        deferred.await()
        cm.flush()
    }

    private fun rewriteToDesktopFacebook(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        if (host == "m.facebook.com" || host == "mbasic.facebook.com" || host == "mobile.facebook.com") {
            return uri.buildUpon().authority("www.facebook.com").build().toString()
        }
        return null
    }

    private fun injectDesktopViewport(view: WebView, desktopWidth: Int) {
        val js = """
            (function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head && document.head.appendChild(meta);
                }
                if (meta) {
                    meta.setAttribute('content', 'width=$desktopWidth, initial-scale=0.35, minimum-scale=0.2, maximum-scale=5.0, user-scalable=yes');
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }
}
