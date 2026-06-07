package com.aneto.instachat.ui.login

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.aneto.instachat.R
import com.aneto.instachat.ui.preview.PreviewTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = MOBILE_UA
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun captureTokens(fbDtsg: String, lsd: String, accountFbid: String) {
                        viewModel.captureGraphTokens(fbDtsg, lsd, accountFbid)
                    }
                },
                "InstaBridge",
            )

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    request?.let { req ->
                        val url = req.url.toString()
                        if (url.contains("/api/v1/") || url.contains("/graphql/")) {
                            val appId = req.requestHeaders["X-IG-App-ID"].orEmpty()
                            val csrf = req.requestHeaders["X-CSRFToken"].orEmpty()
                            if (appId.isNotBlank() || csrf.isNotBlank()) {
                                viewModel.captureHeaders(appId = appId, csrf = csrf)
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loading = false
                    if (url != null && isAuthenticatedUrl(url)) {
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://www.instagram.com") ?: return
                        if (cookies.contains("sessionid=")) {
                            view?.evaluateJavascript(PROBE_FETCH, null)
                            view?.evaluateJavascript(EXTRACT_GRAPH_TOKENS, null)
                            // Persist cookies so the singleton backing WebBridge WebView
                            // sees `sessionid` immediately on boot (and across process death).
                            CookieManager.getInstance().flush()
                            viewModel.onCookiesReady(cookies) {
                                onLoggedIn()
                            }
                        }
                    }
                }
            }
            loadUrl("https://www.instagram.com/accounts/login/")
        }
    }

    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) LoginLoadingOverlay()
            @Suppress("UNUSED_EXPRESSION")
            padding
        }
    }
}

@Composable
fun LoginLoadingOverlay(
    title: String = stringResource(R.string.login_title),
    hint: String = stringResource(R.string.login_hint),
    indicatorStroke: Dp = 3.dp,
    spacingBetweenTitleAndHint: Dp = 8.dp,
    spacingBeforeIndicator: Dp = 32.dp,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(spacingBetweenTitleAndHint))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(spacingBeforeIndicator))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = indicatorStroke,
            )
        }
    }
}

private fun isAuthenticatedUrl(url: String): Boolean {
    val u = url.removeSuffix("/")
    return u == "https://www.instagram.com" ||
        u.startsWith("https://www.instagram.com/direct/") ||
        u == "https://www.instagram.com/?next=" ||
        u.startsWith("https://www.instagram.com/?__coig_login=1")
}

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Instagram 269.0.0.18.75 Android"

private const val PROBE_FETCH = """
(function(){try{fetch('/api/v1/direct_v2/inbox/?limit=1', {credentials:'include'}).catch(()=>{});}catch(e){}})();
"""

private const val EXTRACT_GRAPH_TOKENS = """
(function(){
  try {
    var html = document.documentElement.outerHTML;
    var d = html.match(/"DTSGInitialData",\[\],\{"token":"([^"]+)"/);
    var dtsg = d ? d[1] : '';
    if (!dtsg) {
      var d2 = html.match(/name="fb_dtsg" value="([^"]+)"/);
      dtsg = d2 ? d2[1] : '';
    }
    var l = html.match(/"LSD",\[\],\{"token":"([^"]+)"/);
    var lsd = l ? l[1] : '';
    var a = html.match(/"CurrentUserInitialData",\[\],\{[^}]*"ACCOUNT_ID":"(\d+)"/);
    var accountFbid = a ? a[1] : '';
    if (!accountFbid) {
      var a2 = html.match(/"ACTOR_ID","(\d{15,})"/);
      accountFbid = a2 ? a2[1] : '';
    }
    if (!accountFbid) {
      var a3 = html.match(/"viewerId":"(\d{15,})"/);
      accountFbid = a3 ? a3[1] : '';
    }
    if (window.InstaBridge && window.InstaBridge.captureTokens) {
      window.InstaBridge.captureTokens(dtsg || '', lsd || '', accountFbid || '');
    }
  } catch (e) {}
})();
"""

@Preview(name = "LoginLoadingOverlay", showBackground = true, heightDp = 640)
@Composable
private fun PreviewLoginLoading() {
    PreviewTheme { LoginLoadingOverlay() }
}

@Preview(name = "LoginLoadingOverlay · dark", showBackground = true, heightDp = 640)
@Composable
private fun PreviewLoginLoadingDark() {
    PreviewTheme(dark = true) { LoginLoadingOverlay() }
}

@Preview(name = "LoginLoadingOverlay · textos custom", showBackground = true, heightDp = 640)
@Composable
private fun PreviewLoginLoadingCustom() {
    PreviewTheme {
        LoginLoadingOverlay(
            title = "Signing in...",
            hint = "Validating your session, hang tight.",
            spacingBeforeIndicator = 48.dp,
        )
    }
}
