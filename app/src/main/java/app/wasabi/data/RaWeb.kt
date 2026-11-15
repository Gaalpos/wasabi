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
     * GameShowPagePropsData): series hub, other hubs and similar games. Then,
     * from the same page, fetches the series' games from API v2
     * (/api/v2/hubs/{id}/games, X-API-Key). API v2 sits behind Cloudflare's
     * browser check, so it has to be called from here, not from the app.
     * Reports JSON through WasabiBridge.data(nonce, json), or "" on failure.
     */
    fun gamePageScript(nonce: String): String {
        return """
        (async function () {
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
            if (out.series) {
              // The website's own hub list (what /hub/{id} pages use): public, no sign-in.
              var list = [];
              for (var page = 1; page <= 3; page++) {
                var r = await fetch('/internal-api/hub/' + out.series.id + '/games?sort=releasedAt&page%5Bsize%5D=200&page%5Bnumber%5D=' + page, {
                  credentials: 'same-origin',
                  headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
                });
                if (!r.ok) { out.seriesStatus = r.status; break; }
                var j = await r.json();
                (j.items || []).forEach(function (e) {
                  var g = e.game || {};
                  list.push({ id: g.id, title: g.title || '', badgeUrl: g.badgeUrl || null,
                              system: (g.system && g.system.name) || null,
                              achievements: g.achievementsPublished || 0, points: g.pointsTotal || 0,
                              released: g.releasedAt || null });
                });
                if (!j.lastPage || page >= j.lastPage) break;
              }
              if (!out.seriesStatus) out.seriesGames = list;
            }
            WasabiBridge.data('$nonce', JSON.stringify(out));
          } catch (e) {
            WasabiBridge.data('$nonce', '');
          }
        })();
        """
    }

    /**
     * Reads pages of the website's game list (/internal-api/games, what its
     * All Games page uses) with [query] (sort, filters, page size), up to
     * [pages] pages. Reports {"games":[...]} or {"status":N} through
     * WasabiBridge.data(nonce, json), or "" on failure.
     */
    fun gameListScript(nonce: String, query: String, pages: Int): String = """
        (async function () {
          try {
            var all = [];
            for (var page = 1; page <= $pages; page++) {
              var r = await fetch('/internal-api/games?$query&page%5Bnumber%5D=' + page, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
              });
              if (!r.ok) { WasabiBridge.data('$nonce', JSON.stringify({ status: r.status })); return; }
              var j = await r.json();
              (j.items || []).forEach(function (e) {
                var g = e.game || {};
                all.push({ id: g.id, title: g.title || '', badgeUrl: g.badgeUrl || null,
                           system: (g.system && g.system.name) || null,
                           achievements: g.achievementsPublished || 0, points: g.pointsTotal || 0,
                           players: g.playersTotal || 0 });
              });
              if (!j.lastPage || page >= j.lastPage) break;
            }
            WasabiBridge.data('$nonce', JSON.stringify({ games: all }));
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
