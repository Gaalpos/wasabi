package app.wasabi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.wasabi.data.RaWeb
import app.wasabi.data.WebBridge

/**
 * One-time sign-in on retroachievements.org's own login page, shown inside
 * Wasabi. The password goes straight to the website; Wasabi only keeps the
 * resulting cookies, which the hidden WebView below uses for Want to Play.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSignInScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill("Back", onClick = { vm.back() }, icon = Icons.AutoMirrored.Rounded.ArrowBack)
            Column(Modifier.weight(1f)) {
                Text("Sign in to RetroAchievements", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Once, so Wasabi can add games to Want to Play. Your password goes to retroachievements.org, never to Wasabi.",
                    color = Palette.Dim, fontSize = 11.sp,
                )
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        private var done = false

                        // Stay on retroachievements.org.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            !RaWeb.isRaUrl(request.url)

                        override fun onPageFinished(view: WebView, url: String) {
                            if (done) return
                            if (RaWeb.isAuthPage(url)) {
                                view.evaluateJavascript(RaWeb.REMEMBER_ME_JS, null)
                            } else if (RaWeb.isRaUrl(android.net.Uri.parse(url))) {
                                // Left the login pages: signed in.
                                done = true
                                CookieManager.getInstance().flush()
                                vm.onWebSignedIn()
                            }
                        }
                    }
                    loadUrl("${RaWeb.ORIGIN}/login")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/**
 * Invisible WebView that runs one Want to Play request with the website
 * session, then goes away. It exists only while [AppViewModel.webJob] is set.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun RaWebWorker(vm: AppViewModel) {
    val job = vm.webJob ?: return
    key(job.nonce) {
        AndroidView(
            modifier = Modifier.size(1.dp).alpha(0f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(WebBridge(onDone = { nonce, status -> post { vm.onWebResult(nonce, status) } }), "WasabiBridge")
                    webViewClient = object : WebViewClient() {
                        private var sent = false

                        override fun onPageFinished(view: WebView, url: String) {
                            if (sent) return
                            sent = true
                            view.evaluateJavascript(RaWeb.wantToPlayScript(job.gameId, job.add, job.nonce), null)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame && !sent) {
                                sent = true
                                post { vm.onWebResult(job.nonce, -1) }
                            }
                        }
                    }
                    // A tiny same-origin page, so the script's requests carry the site's cookies.
                    loadUrl("${RaWeb.ORIGIN}/robots.txt")
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/**
 * Invisible WebView that opens a public website page (a game page) and reads
 * its embedded data, for Series and Similar. No sign-in needed. It exists
 * only while [AppViewModel.pageJob] is set; images are not loaded.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun RaPageWorker(vm: AppViewModel) {
    val job = vm.pageJob ?: return
    key(job.nonce) {
        AndroidView(
            modifier = Modifier.size(1.dp).alpha(0f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    addJavascriptInterface(WebBridge(onData = { nonce, json -> post { vm.onPageData(nonce, json) } }), "WasabiBridge")
                    webViewClient = object : WebViewClient() {
                        private var sent = false

                        override fun onPageFinished(view: WebView, url: String) {
                            if (sent) return
                            sent = true
                            view.evaluateJavascript(RaWeb.gamePageScript(job.nonce), null)
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame && !sent) {
                                sent = true
                                post { vm.onPageData(job.nonce, null) }
                            }
                        }
                    }
                    loadUrl(job.url)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
