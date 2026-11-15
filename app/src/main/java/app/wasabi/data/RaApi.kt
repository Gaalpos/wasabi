package app.wasabi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

class ApiException(message: String) : Exception(message)

/**
 * Minimal client for the RetroAchievements Web API
 * (https://api-docs.retroachievements.org). Every call is authenticated with
 * the user's personal Web API key, passed as `y`.
 */
class RaApi(val user: String, private val key: String) {


    suspend fun profile(of: String = user): Profile {
        val j = obj("API_GetUserProfile.php", "u" to of)
        return Profile(
            user = j.str("User") ?: of,
            avatarUrl = media(j.str("UserPic")),
            motto = j.str("Motto"),
            richPresence = j.str("RichPresenceMsg"),
            points = j.optInt("TotalPoints"),
            softPoints = j.optInt("TotalSoftcorePoints"),
            truePoints = j.optInt("TotalTruePoints"),
            memberSince = j.str("MemberSince"),
        )
    }

    suspend fun recentGames(of: String = user, count: Int = 50): List<RecentGame> =
        arr("API_GetUserRecentlyPlayedGames.php", "u" to of, "c" to "$count").objects().map { j ->
            RecentGame(
                id = j.optInt("GameID"),
                title = j.str("Title").orEmpty(),
                console = j.str("ConsoleName").orEmpty(),
                iconUrl = media(j.str("ImageIcon")),
                lastPlayed = j.str("LastPlayed"),
                total = j.optInt("AchievementsTotal"),
                earned = j.optInt("NumAchieved"),
                earnedHc = j.optInt("NumAchievedHardcore"),
            )
        }.distinctBy { it.id }

    /** Every game the user has unlocked something in (paged, 500 per call). */
    suspend fun library(): List<LibraryGame> {
        val out = mutableListOf<LibraryGame>()
        var offset = 0
        while (offset < 5000) {
            val page = obj("API_GetUserCompletionProgress.php", "u" to user, "c" to "500", "o" to "$offset")
            val results = page.optJSONArray("Results")?.objects().orEmpty()
            results.mapTo(out) { j ->
                LibraryGame(
                    id = j.optInt("GameID"),
                    title = j.str("Title").orEmpty(),
                    console = j.str("ConsoleName").orEmpty(),
                    iconUrl = media(j.str("ImageIcon")),
                    total = j.optInt("MaxPossible"),
                    earned = j.optInt("NumAwarded"),
                    earnedHc = j.optInt("NumAwardedHardcore"),
                    award = j.str("HighestAwardKind"),
                    lastUnlock = j.str("MostRecentAwardedDate"),
                )
            }
            offset += results.size
            if (results.size < 500 || offset >= page.optInt("Total")) break
        }
        return out.distinctBy { it.id }
    }

    /** The user's "Want to Play" list from RetroAchievements (paged, 500 per call). */
    suspend fun wantToPlay(): List<WantGame> {
        val out = mutableListOf<WantGame>()
        var offset = 0
        while (offset < 5000) {
            val page = obj("API_GetUserWantToPlayList.php", "u" to user, "c" to "500", "o" to "$offset")
            val results = page.optJSONArray("Results")?.objects().orEmpty()
            results.mapTo(out) { j ->
                WantGame(
                    id = j.optInt("ID"),
                    title = j.str("Title").orEmpty(),
                    console = j.str("ConsoleName").orEmpty(),
                    iconUrl = media(j.str("ImageIcon")),
                    achievements = j.optInt("AchievementsPublished"),
                    points = j.optInt("PointsTotal"),
                )
            }
            offset += results.size
            if (results.size < 500 || offset >= page.optInt("Total")) break
        }
        return out.distinctBy { it.id }
    }

    /** Unlocks from the last 30 days, newest first. */
    suspend fun recentUnlocks(of: String = user): List<Unlock> =
        arr("API_GetUserRecentAchievements.php", "u" to of, "m" to "${60 * 24 * 30}").objects().map { j ->
            Unlock(
                achievementId = j.optInt("AchievementID"),
                title = j.str("Title").orEmpty(),
                description = j.str("Description").orEmpty(),
                points = j.optInt("Points"),
                badgeUrl = media(j.str("BadgeURL")),
                date = j.str("Date"),
                hardcore = j.opt("HardcoreMode").let { it == true || it == 1 || it == "1" },
                gameId = j.optInt("GameID"),
                gameTitle = j.str("GameTitle").orEmpty(),
                console = j.str("ConsoleName").orEmpty(),
            )
        }.distinctBy { "${it.achievementId}:${it.date}" }

    /** People the signed-in user follows (paged, 500 per call). */
    suspend fun following(): List<FollowedUser> {
        val out = mutableListOf<FollowedUser>()
        var offset = 0
        while (offset < 5000) {
            val page = obj("API_GetUsersIFollow.php", "c" to "500", "o" to "$offset")
            val results = page.optJSONArray("Results")?.objects().orEmpty()
            results.mapTo(out) { j ->
                val name = j.str("User").orEmpty()
                FollowedUser(
                    user = name,
                    avatarUrl = "$MEDIA/UserPic/$name.png",
                    points = j.optInt("Points"),
                    softPoints = j.optInt("PointsSoftcore"),
                    followsBack = j.opt("IsFollowingMe").let { it == true || it == 1 || it == "1" },
                )
            }
            offset += results.size
            if (results.size < 500 || offset >= page.optInt("Total")) break
        }
        return out.filter { it.user.isNotEmpty() }.distinctBy { it.user.lowercase() }
    }

    /** Game systems that are active on RetroAchievements, sorted by name. */
    suspend fun consoles(): List<Console> =
        arr("API_GetConsoleIDs.php", "a" to "1", "g" to "1").objects().map { j ->
            Console(
                id = j.optInt("ID"),
                name = j.str("Name").orEmpty(),
                iconUrl = media(j.str("IconURL")),
            )
        }.filter { it.id > 0 && it.name.isNotEmpty() }.distinctBy { it.id }.sortedBy { it.name.lowercase() }

    /** Every game on a console that has achievements, sorted by title. */
    suspend fun consoleGames(consoleId: Int, consoleName: String = ""): List<ConsoleGame> =
        arr("API_GetGameList.php", "i" to "$consoleId", "f" to "1").objects().map { j ->
            ConsoleGame(
                id = j.optInt("ID"),
                title = j.str("Title").orEmpty(),
                iconUrl = media(j.str("ImageIcon")),
                achievements = j.optInt("NumAchievements"),
                points = j.optInt("Points"),
                console = j.str("ConsoleName") ?: consoleName,
            )
        }.distinctBy { it.id }.sortedBy { it.title.lowercase() }

    /** [who]'s progress in game [gameId]: (softcore count, hardcore count). */
    suspend fun progressIn(who: String, gameId: Int): Pair<Int, Int> {
        val body = get("API_GetUserProgress.php", arrayOf("u" to who, "i" to "$gameId")).trim()
        if (!body.startsWith("{")) return 0 to 0   // [] for unknown users
        val j = JSONObject(body)
        val p = j.optJSONObject("$gameId") ?: return 0 to 0
        return p.optInt("NumAchieved") to p.optInt("NumAchievedHardcore")
    }

    suspend fun game(id: Int, of: String = user): GameDetail {
        val j = obj("API_GetGameInfoAndUserProgress.php", "g" to "$id", "u" to of, "a" to "1")
        // "Achievements" is an object keyed by id, or an empty array when there are none.
        val achObj = j.optJSONObject("Achievements")
        val achievements = achObj?.keys()?.asSequence()?.mapNotNull { achObj.optJSONObject(it) }?.map { a ->
            Achievement(
                id = a.optInt("ID"),
                title = a.str("Title").orEmpty(),
                description = a.str("Description").orEmpty(),
                points = a.optInt("Points"),
                badge = a.str("BadgeName") ?: "00000",
                order = a.optInt("DisplayOrder"),
                type = a.str("Type"),
                earnedAt = a.str("DateEarned"),
                earnedHcAt = a.str("DateEarnedHardcore"),
                numAwarded = a.optInt("NumAwarded"),
                numAwardedHc = a.optInt("NumAwardedHardcore"),
                trueRatio = a.optInt("TrueRatio"),
                author = a.str("Author"),
            )
        }?.sortedWith(compareBy({ it.order }, { it.id }))?.toList().orEmpty()

        return GameDetail(
            id = j.optInt("ID", id),
            title = j.str("Title").orEmpty(),
            console = j.str("ConsoleName").orEmpty(),
            iconUrl = media(j.str("ImageIcon")),
            players = j.optInt("NumDistinctPlayers"),
            award = j.str("HighestAwardKind"),
            achievements = achievements,
            titleImageUrl = media(j.str("ImageTitle")),
            ingameImageUrl = media(j.str("ImageIngame")),
            boxArtUrl = media(j.str("ImageBoxArt")),
            developer = j.str("Developer"),
            publisher = j.str("Publisher"),
            genre = j.str("Genre"),
            released = j.str("Released"),
            releasedPrecision = j.str("ReleasedAtGranularity"),
            playersHardcore = j.optInt("NumDistinctPlayersHardcore"),
            playersSoftcore = j.optInt("NumDistinctPlayersCasual"),
            playtimeSeconds = j.optLong("UserTotalPlaytime"),
        )
    }

    // ---- plumbing ----

    private suspend fun obj(endpoint: String, vararg params: Pair<String, String>): JSONObject {
        val j = JSONObject(get(endpoint, params))
        // Some failures come back as 200 with only a message.
        if (j.length() <= 2 && j.has("message") && !j.has("User") && !j.has("ID")) {
            throw ApiException(j.optString("message"))
        }
        return j
    }

    private suspend fun arr(endpoint: String, vararg params: Pair<String, String>): JSONArray {
        val body = get(endpoint, params).trim()
        if (body.startsWith("{")) {
            throw ApiException(JSONObject(body).optString("message", "Unexpected response from server"))
        }
        return JSONArray(body)
    }

    private suspend fun get(endpoint: String, params: Array<out Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val query = (params.toList() + ("y" to key)).joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, "UTF-8")}"
            }
            val conn = URL("$BASE/$endpoint?$query").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Wasabi/1.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                when (code) {
                    in 200..299 -> body
                    401, 403 -> throw ApiException("Username or Web API key not accepted.")
                    404 -> throw ApiException("Not found on RetroAchievements.")
                    429 -> throw ApiException("Too many requests. Wait a moment and retry.")
                    else -> throw ApiException("RetroAchievements returned an error ($code).")
                }
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        private const val BASE = "https://retroachievements.org/API"

        /** Parses the JSON that RaWeb.gamePageScript reports. */
        fun parseExtras(json: String): GameExtras {
            val j = JSONObject(json)
            fun hub(h: JSONObject) = HubRef(h.optInt("id"), h.str("title").orEmpty(), h.optInt("games"))
            return GameExtras(
                series = j.optJSONObject("series")?.let(::hub)?.takeIf { it.id > 0 },
                hubs = j.optJSONArray("hubs")?.objects().orEmpty().map(::hub).filter { it.id > 0 },
                similar = j.optJSONArray("similar")?.objects().orEmpty().map { g ->
                    RelatedGame(
                        id = g.optInt("id"),
                        title = g.str("title").orEmpty(),
                        iconUrl = media(g.str("badgeUrl")),
                        console = g.str("system"),
                        achievements = g.optInt("achievements"),
                        points = g.optInt("points"),
                    )
                }.filter { it.id > 0 },
                seriesGames = j.optJSONArray("seriesGames")?.objects()?.map { g ->
                    RelatedGame(
                        id = g.optInt("id"),
                        title = g.str("title").orEmpty(),
                        iconUrl = media(g.str("badgeUrl")),
                        console = g.str("system"),
                        achievements = g.optInt("achievements"),
                        points = g.optInt("points"),
                        released = g.str("released"),
                    )
                }?.filter { it.id > 0 },
                seriesStatus = if (j.has("seriesStatus")) j.optInt("seriesStatus") else null,
            )
        }

        fun media(path: String?): String? = when {
            path.isNullOrBlank() -> null
            path.startsWith("http") -> path
            else -> MEDIA + (if (path.startsWith("/")) path else "/$path")
        }

        fun friendly(e: Throwable): String = when (e) {
            is ApiException -> e.message ?: "Something went wrong."
            is UnknownHostException -> "No connection. Check Wi-Fi."
            is SocketTimeoutException -> "The server took too long. Try again."
            is IOException -> "Network error: ${e.message ?: "unknown"}"
            is JSONException -> "Unexpected response from the server."
            else -> e.message ?: "Something went wrong."
        }
    }
}

private fun JSONObject.str(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Saves the username and Web API key on the device (app-private, no backup). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("wasabi", Context.MODE_PRIVATE)
    val user: String? get() = sp.getString("user", null)
    val key: String? get() = sp.getString("key", null)
    fun save(user: String, key: String) = sp.edit().putString("user", user).putString("key", key).apply()
    fun clearKey() = sp.edit().remove("key").apply()

    /** Name of the chosen accent color. */
    var accent: String?
        get() = sp.getString("accent", null)
        set(v) = sp.edit().putString("accent", v).apply()

    /** Signed in to the retroachievements.org website (for Want to Play). */
    var webSignedIn: Boolean
        get() = sp.getBoolean("webSignedIn", false)
        set(v) = sp.edit().putBoolean("webSignedIn", v).apply()

    /** Enabled home tabs as comma-separated names; null means all. */
    var tabs: String?
        get() = sp.getString("tabs", null)
        set(v) = sp.edit().putString("tabs", v).apply()

    /**
     * Games hidden from the Playing tab (Wasabi only; RA is unchanged), with
     * the game's "last played" time when it was hidden: playing it again
     * makes it reappear. Stored as "id=time;id=time" (older "id,id" too).
     */
    var hiddenPlaying: Map<Int, String>
        get() = sp.getString("hiddenPlaying", null).orEmpty().split(';', ',').mapNotNull { e ->
            val id = e.substringBefore('=').trim().toIntOrNull() ?: return@mapNotNull null
            id to e.substringAfter('=', "")
        }.toMap()
        set(v) = sp.edit().putString("hiddenPlaying", v.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()

    /** Name of the button that refreshes (see RefreshButton). */
    var refreshButton: String?
        get() = sp.getString("refreshButton", null)
        set(v) = sp.edit().putString("refreshButton", v).apply()

    /** Favorite system (console) ids, shown first in Browse. */
    var favoriteConsoles: Set<Int>
        get() = sp.getString("favConsoles", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
        set(v) = sp.edit().putString("favConsoles", v.joinToString(",")).apply()
}
