package app.wasabi.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
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
        ).let { p ->
            // Site rank comes from the user summary.
            val summary = runCatching { obj("API_GetUserSummary.php", "u" to of, "g" to "0", "a" to "0") }.getOrNull()
            if (summary == null) p
            else p.copy(rank = if (summary.isNull("Rank")) null else summary.optInt("Rank").takeIf { it > 0 }, totalRanked = summary.optInt("TotalRanked"))
        }
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
    suspend fun recentUnlocks(of: String = user, minutes: Int = 60 * 24 * 30): List<Unlock> =
        arr("API_GetUserRecentAchievements.php", "u" to of, "m" to "$minutes").objects().map { j ->
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
                updated = j.str("DateModified"),
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

    // ---- awards, followers, stats ----

    /** The awards wall: counts and the visible badges, newest first. */
    suspend fun awards(of: String = user): Awards {
        val j = obj("API_GetUserAwards.php", "u" to of)
        val items = j.optJSONArray("VisibleUserAwards")?.objects().orEmpty().map { a ->
            val type = a.str("AwardType").orEmpty()
            val extra = a.optInt("AwardDataExtra")
            val kind = when (type) {
                "Mastery/Completion" -> if (extra == 1) "mastered" else "completed"
                "Game Beaten" -> if (extra == 1) "beaten-hardcore" else "beaten-softcore"
                "Event" -> "event"
                else -> "site"
            }
            AwardItem(
                kind = kind,
                title = a.str("Title") ?: type,
                console = a.str("ConsoleName").orEmpty(),
                iconUrl = media(a.str("ImageIcon")),
                date = a.str("AwardedAt"),
                gameId = if (kind == "event" || kind == "site") null else a.optInt("AwardData").takeIf { it > 0 },
            )
        }.sortedByDescending { it.date.orEmpty() }
        return Awards(
            mastered = j.optInt("MasteryAwardsCount"),
            completed = j.optInt("CompletionAwardsCount"),
            beatenHardcore = j.optInt("BeatenHardcoreAwardsCount"),
            beatenSoftcore = j.optInt("BeatenSoftcoreAwardsCount"),
            events = j.optInt("EventAwardsCount"),
            site = j.optInt("SiteAwardsCount"),
            items = items,
        )
    }

    /** People who follow the signed-in user; followsBack = you follow them too. */
    suspend fun followers(): List<FollowedUser> {
        val out = mutableListOf<FollowedUser>()
        var offset = 0
        while (offset < 5000) {
            val page = obj("API_GetUsersFollowingMe.php", "c" to "500", "o" to "$offset")
            val results = page.optJSONArray("Results")?.objects().orEmpty()
            results.mapTo(out) { j ->
                val name = j.str("User").orEmpty()
                FollowedUser(name, "$MEDIA/UserPic/$name.png", j.optInt("Points"), j.optInt("PointsSoftcore"),
                    followsBack = j.opt("AmIFollowing").let { it == true || it == 1 || it == "1" })
            }
            offset += results.size
            if (results.size < 500 || offset >= page.optInt("Total")) break
        }
        return out.filter { it.user.isNotEmpty() }.distinctBy { it.user.lowercase() }
    }

    /** Achievements unlocked between two times (Unix seconds), oldest first. */
    suspend fun earnedBetween(from: Long, to: Long, of: String = user): List<Unlock> =
        arr("API_GetAchievementsEarnedBetween.php", "u" to of, "f" to "$from", "t" to "$to").objects().map { j ->
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
        }.sortedBy { it.date.orEmpty() }

    // ---- games ----

    /** A game's leaderboards with each leader, merged with your own entries. */
    suspend fun leaderboards(gameId: Int): List<LeaderboardRow> {
        val all = obj("API_GetGameLeaderboards.php", "i" to "$gameId", "c" to "500").optJSONArray("Results")?.objects().orEmpty()
        val mine = runCatching {
            obj("API_GetUserGameLeaderboards.php", "i" to "$gameId", "u" to user, "c" to "500").optJSONArray("Results")?.objects().orEmpty()
        }.getOrDefault(emptyList()).associateBy { it.optInt("ID") }
        return all.map { l ->
            val id = l.optInt("ID")
            val top = l.optJSONObject("TopEntry")
            val me = mine[id]?.optJSONObject("UserEntry")
            LeaderboardRow(
                id = id,
                title = l.str("Title").orEmpty(),
                description = l.str("Description").orEmpty(),
                leader = top?.str("User"),
                leaderScore = top?.str("FormattedScore"),
                myRank = me?.optInt("Rank")?.takeIf { it > 0 },
                myScore = me?.str("FormattedScore"),
            )
        }.filter { it.id > 0 }
    }

    /** Recent unlockers (newest first) and the latest comments for an achievement. */
    suspend fun achievementInfo(id: Int): AchievementInfo {
        val j = obj("API_GetAchievementUnlocks.php", "a" to "$id", "c" to "50")
        val comments = runCatching {
            obj("API_GetComments.php", "i" to "$id", "t" to "2", "c" to "50", "sort" to "-submitted")
                .optJSONArray("Results")?.objects().orEmpty().map { c ->
                    Comment(c.str("User").orEmpty(), c.str("Submitted"), c.str("CommentText").orEmpty())
                }
        }.getOrDefault(emptyList())
        return AchievementInfo(
            unlocks = j.optInt("UnlocksCount"),
            unlocksHardcore = j.optInt("UnlocksHardcoreCount"),
            players = j.optInt("TotalPlayers"),
            recent = j.optJSONArray("Unlocks")?.objects().orEmpty().map { u ->
                AchievementUnlock(u.str("User").orEmpty(), u.str("DateAwarded"), u.opt("HardcoreMode").let { it == true || it == 1 || it == "1" })
            }.sortedByDescending { it.date.orEmpty() },
            comments = comments,
        )
    }

    /**
     * Median time to beat [gameId] in seconds, hardcore first, or 0 when too
     * few players have beaten it (the website needs at least 5, too).
     */
    suspend fun beatTime(gameId: Int): Int {
        val j = obj("API_GetGameProgression.php", "i" to "$gameId")
        val hc = j.optInt("MedianTimeToBeatHardcore")
        val sc = j.optInt("MedianTimeToBeat")
        return when {
            hc > 0 && j.optInt("TimesUsedInHardcoreBeatMedian") >= 5 -> hc
            sc > 0 && j.optInt("TimesUsedInBeatMedian") >= 5 -> sc
            else -> 0
        }
    }

    // ---- community ----

    suspend fun achievementOfTheWeek(): WeeklyAchievement {
        val j = obj("API_GetAchievementOfTheWeek.php")
        val a = j.optJSONObject("Achievement") ?: JSONObject()
        val g = j.optJSONObject("Game") ?: JSONObject()
        return WeeklyAchievement(
            id = a.optInt("ID"),
            title = a.str("Title").orEmpty(),
            description = a.str("Description").orEmpty(),
            points = a.optInt("Points"),
            badgeUrl = media(a.str("BadgeURL")),
            gameId = g.optInt("ID"),
            gameTitle = g.str("Title").orEmpty(),
            console = j.optJSONObject("Console")?.str("Title").orEmpty(),
            unlocks = j.optInt("UnlocksCount"),
            players = j.optInt("TotalPlayers"),
        )
    }

    /** New achievement sets being made right now (active claims), newest first. */
    suspend fun comingSoon(): List<FeedItem> =
        arr("API_GetActiveClaims.php").objects()
            .filter { it.optInt("SetType") == 0 && it.optInt("ClaimType") == 0 }
            .map { c ->
                val inReview = c.optInt("Status") == 3
                FeedItem(
                    key = "claim:${c.optInt("ID")}",
                    gameId = c.optInt("GameID"),
                    title = c.str("GameTitle").orEmpty(),
                    console = c.str("ConsoleName").orEmpty(),
                    iconUrl = media(c.str("GameIcon")),
                    detail = if (inReview) "In review" else "In development",
                    user = c.str("User"),
                    date = c.str("Created"),
                )
            }.filter { it.gameId > 0 }.distinctBy { it.gameId }.sortedByDescending { it.date.orEmpty() }

    /** Masteries and beaten awards across RetroAchievements, newest first. */
    suspend fun recentGameAwards(kinds: String? = null, count: Int = 100): List<FeedItem> {
        val params = listOfNotNull("c" to "$count", kinds?.let { "k" to it }).toTypedArray()
        return obj("API_GetRecentGameAwards.php", *params).optJSONArray("Results")?.objects().orEmpty().mapIndexed { i, a ->
            val kind = a.str("AwardKind").orEmpty()
            FeedItem(
                key = "award:$i:${a.optInt("GameID")}:${a.str("User")}",
                gameId = a.optInt("GameID"),
                title = a.str("GameTitle").orEmpty(),
                console = a.str("ConsoleName").orEmpty(),
                iconUrl = null,
                detail = when (kind) {
                    "mastered" -> "Mastered"
                    "completed" -> "Completed"
                    "beaten-hardcore" -> "Beaten"
                    else -> "Beaten (softcore)"
                },
                user = a.str("User"),
                date = a.str("AwardDate"),
            )
        }.filter { it.gameId > 0 }
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
            // Offline mode: every good answer is saved; without a connection the
            // saved one is used and [offline] is set so the app can say so.
            val cacheFile = cacheDir?.let { File(it, "api/" + cacheName(endpoint, params)) }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                when (code) {
                    in 200..299 -> {
                        offline = false
                        cacheFile?.let { f -> runCatching { f.parentFile?.mkdirs(); f.writeText(body) } }
                        body
                    }
                    401, 403 -> throw ApiException("Username or Web API key not accepted.")
                    404 -> throw ApiException("Not found on RetroAchievements.")
                    429 -> throw ApiException("Too many requests. Wait a moment and retry.")
                    else -> throw ApiException("RetroAchievements returned an error ($code).")
                }
            } catch (e: IOException) {
                val saved = cacheFile?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() } ?: throw e
                offline = true
                saved
            } finally {
                conn.disconnect()
            }
        }

    /** Cache file name for a request (the key is left out). */
    private fun cacheName(endpoint: String, params: Array<out Pair<String, String>>): String {
        val id = endpoint + "?" + params.joinToString("&") { "${it.first}=${it.second}" }
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$hash.json"
    }

    companion object {
        private const val BASE = "https://retroachievements.org/API"

        /** Where answers are saved for offline use (set by the app at start). */
        var cacheDir: File? = null

        /** True while the last request had to use saved data (no connection). */
        var offline by mutableStateOf(false)

        /** Parses RaWeb.gameListScript's report: the games, or the HTTP status that stopped it. */
        fun parseGameList(json: String): Pair<List<RelatedGame>?, Int?> {
            val j = JSONObject(json)
            if (j.has("status")) return null to j.optInt("status")
            return j.optJSONArray("games")?.objects().orEmpty().map { g ->
                RelatedGame(
                    id = g.optInt("id"),
                    title = g.str("title").orEmpty(),
                    iconUrl = media(g.str("badgeUrl")),
                    console = g.str("system"),
                    achievements = g.optInt("achievements"),
                    points = g.optInt("points"),
                    players = g.optInt("players"),
                    beatTime = if (g.isNull("beat")) null else g.optInt("beat"),
                )
            }.filter { it.id > 0 }.distinctBy { it.id } to null
        }

        /** Parses the JSON that RaWeb.gamePageScript reports. */
        fun parseExtras(json: String): GameExtras {
            val j = JSONObject(json)
            fun hub(h: JSONObject) = HubRef(h.optInt("id"), h.str("title").orEmpty(), h.optInt("games"))
            return GameExtras(
                timeToBeat = j.optInt("timeToBeat").takeIf { it > 0 },
                timeToBeatHardcore = j.optInt("timeToBeatHardcore").takeIf { it > 0 },
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
                        beatTime = if (g.isNull("beat")) null else g.optInt("beat"),
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

    /**
     * Settings kept only on this device (nothing that comes from the API),
     * for Settings → Backup. The account, API key and website sign-in are
     * left out on purpose.
     */
    private val backupKeys = listOf(
        "accent", "tabs", "tabOrder", "refreshButton", "favConsoles",
        "hiddenPlaying", "hiddenBrowseLists", "notifications", "achSort",
    )

    fun exportBackup(): String {
        val settings = org.json.JSONObject()
        backupKeys.forEach { k ->
            when (val v = sp.all[k]) {
                null -> Unit
                is Set<*> -> settings.put(k, org.json.JSONArray(v.map { it.toString() }))
                else -> settings.put(k, v)
            }
        }
        return org.json.JSONObject()
            .put("app", "wasabi").put("backup", 1).put("settings", settings)
            .toString(2)
    }

    /** Restores a backup from [exportBackup]; returns how many settings it set. */
    fun importBackup(text: String): Int {
        val root = org.json.JSONObject(text)
        require(root.optString("app") == "wasabi") { "This isn't a Wasabi backup" }
        val settings = root.getJSONObject("settings")
        val e = sp.edit()
        var n = 0
        backupKeys.filter { settings.has(it) }.forEach { k ->
            when (val v = settings.get(k)) {
                is org.json.JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                is Boolean -> e.putBoolean(k, v)
                else -> e.putString(k, v.toString())
            }
            n++
        }
        e.apply()
        return n
    }

    /** Name of the chosen accent color. */
    var accent: String?
        get() = sp.getString("accent", null)
        set(v) = sp.edit().putString("accent", v).apply()

    /** Signed in to the retroachievements.org website (for Want to Play). */
    var webSignedIn: Boolean
        get() = sp.getBoolean("webSignedIn", false)
        set(v) = sp.edit().putBoolean("webSignedIn", v).apply()

    /** Achievement order on game pages (AchSort name). */
    var achSort: String?
        get() = sp.getString("achSort", null)
        set(v) = sp.edit().putString("achSort", v).apply()

    /** Home tab order as comma-separated names; null means the default order. */
    var tabOrder: String?
        get() = sp.getString("tabOrder", null)
        set(v) = sp.edit().putString("tabOrder", v).apply()

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

    /** Background notifications for new unlocks and friends' masteries. */
    var notifications: Boolean
        get() = sp.getBoolean("notifications", false)
        set(v) = sp.edit().putBoolean("notifications", v).apply()

    /** Newest unlock / friend award already notified (empty: not started yet). */
    var lastUnlockSeen: String
        get() = sp.getString("lastUnlockSeen", "").orEmpty()
        set(v) = sp.edit().putString("lastUnlockSeen", v).apply()
    var lastAwardSeen: String
        get() = sp.getString("lastAwardSeen", "").orEmpty()
        set(v) = sp.edit().putString("lastAwardSeen", v).apply()

    /** Who you follow (lowercase), for mastery notifications. */
    var followingNames: Set<String>
        get() = sp.getStringSet("followingNames", emptySet()).orEmpty()
        set(v) = sp.edit().putStringSet("followingNames", v).apply()

    /** List widgets: each one's rows as a JSON array (see Widgets.kt). */
    fun widgetData(key: String): String? = sp.getString(key, null)
    fun setWidgetData(key: String, v: String?) = sp.edit().putString(key, v).remove("widgetUnlocks").apply()

    /** Home screen widget: placed or not, and what it shows. */
    var widgetPlaced: Boolean
        get() = sp.getBoolean("widgetPlaced", false)
        set(v) = sp.edit().putBoolean("widgetPlaced", v).apply()
    val widgetUser: String? get() = sp.getString("widgetUser", null)
    val widgetPoints: Int get() = sp.getInt("widgetPoints", -1)
    val widgetLine: String? get() = sp.getString("widgetLine", null)
    fun saveWidget(user: String?, points: Int, line: String?) =
        sp.edit().putString("widgetUser", user).putInt("widgetPoints", points).putString("widgetLine", line).apply()

    /** Browse lists turned off in Settings (CommunityKind names). */
    var hiddenBrowseLists: Set<String>
        get() = sp.getStringSet("hiddenBrowseLists", emptySet()).orEmpty()
        set(v) = sp.edit().putStringSet("hiddenBrowseLists", v).apply()

    /** Name of the button that refreshes (see RefreshButton). */
    var refreshButton: String?
        get() = sp.getString("refreshButton", null)
        set(v) = sp.edit().putString("refreshButton", v).apply()

    /** Favorite system (console) ids, shown first in Browse. */
    var favoriteConsoles: Set<Int>
        get() = sp.getString("favConsoles", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
        set(v) = sp.edit().putString("favConsoles", v.joinToString(",")).apply()
}
