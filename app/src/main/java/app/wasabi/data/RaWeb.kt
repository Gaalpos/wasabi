package app.wasabi.data

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface

/**
 * The retroachievements.org website session, used only to add and remove
 * Want to Play games: the Web API is read-only (v1 and v2), so Wasabi uses
 * the same requests the website itself sends (see RAWeb's
 * useAddToGameListMutation / useRemoveFromGameListMutation):
 *   POST   /internal-api/user-game-list/{gameId}  {"userGameListType":"play"}
 *   DELETE /internal-api/user-game-list/{gameId}  {"userGameListType":"play"}
 * They run inside a hidden WebView with the cookies from a one-time sign-in
 * on the real login page, so Wasabi never sees the password.
 */
object RaWeb {
    const val ORIGIN = "https://retroachievements.org"

    /** Pages that are part of signing in (anything else means signed in). */
    private val authPaths = listOf("/login", "/register", "/forgot-password", "/reset-password", "/password", "/two-factor", "/email")

    fun isAuthPage(url: String?): Boolean {
        val path = url?.let { Uri.parse(it).path }.orEmpty()
        return authPaths.any { path.startsWith(it) }
    }

    fun isRaUrl(uri: Uri?): Boolean {
        val host = uri?.host ?: return false
        return host == "retroachievements.org" || host.endsWith(".retroachievements.org")
    }

    /** Forget the website session (cookies) on this device. */
    fun signOut() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }

    /** Ticks "Remember me" on the login form so the session lasts. */
    const val REMEMBER_ME_JS = """
        (function () {
          var box = document.querySelector('input[name="remember"]');
          if (box && !box.checked) { box.click(); }
        })();
    """

    /**
     * Reads a game page's embedded data (the website's Inertia page props:
     * GameShowPagePropsData) and reports the series hub, other hubs and
     * similar games through WasabiBridge.data(nonce, json), or "" on failure.
     */
    fun gamePageScript(nonce: String): String = """
        (function () {
          try {
            var raw = null;
            var app = document.getElementById('app');
            if (app) raw = app.getAttribute('data-page');
            if (!raw) { var s = document.querySelector('script[data-page]'); raw = s && s.textContent; }
            var p = JSON.parse(raw).props || {};
            var hub = function (h) { return { id: h.id, title: h.title || '', games: h.gameCount || 0 }; };
            var game = function (g) {
              return { id: g.id, title: g.title || '', badgeUrl: g.badgeUrl || null,
                       system: (g.system && g.system.name) || null,
                       achievements: g.achievementsPublished || 0, points: g.pointsTotal || 0 };
            };
            var out = {
              series: p.seriesHub && p.seriesHub.hub
                ? { id: p.seriesHub.hub.id, title: p.seriesHub.hub.title || '', games: p.seriesHub.totalGameCount || 0 }
                : null,
              hubs: (p.hubs || []).map(hub),
              similar: (p.similarGames || []).map(game)
            };
            WasabiBridge.data('$nonce', JSON.stringify(out));
          } catch (e) {
            WasabiBridge.data('$nonce', '');
          }
        })();
    """

    /**
     * Adds ([add] = true) or removes a game from Want to Play and reports
     * the HTTP status through WasabiBridge.done(nonce, status): 2xx done,
     * 401/419 signed out, -1 network error.
     */
    fun wantToPlayScript(gameId: Int, add: Boolean, nonce: String): String = """
        (async function () {
          var report = function (s) { WasabiBridge.done('$nonce', s); };
          try {
            var json = { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' };
            // A first authenticated request refreshes the session and its XSRF cookie.
            var check = await fetch('/internal-api/user-game-list/random', { credentials: 'same-origin', headers: json });
            if (check.status === 401 || check.url.indexOf('/login') >= 0) { report(401); return; }
            var m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
            var token = m ? decodeURIComponent(m[1]) : '';
            var r = await fetch('/internal-api/user-game-list/$gameId', {
              method: '${if (add) "POST" else "DELETE"}',
              credentials: 'same-origin',
              headers: Object.assign({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token }, json),
              body: JSON.stringify({ userGameListType: 'play' })
            });
            report(r.status);
          } catch (e) {
            report(-1);
          }
        })();
    """
}

/** Receives results from the hidden WebView's scripts. */
class WebBridge(
    private val onDone: (nonce: String, status: Int) -> Unit = { _, _ -> },
    private val onData: (nonce: String, json: String) -> Unit = { _, _ -> },
) {
    @JavascriptInterface
    fun done(nonce: String, status: Int) = onDone(nonce, status)

    @JavascriptInterface
    fun data(nonce: String, json: String) = onData(nonce, json)
}
