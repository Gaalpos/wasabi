package app.wasabi.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every game with achievements, across all consoles, for Search.
 * The Web API has no search endpoint, so the catalog is built from each
 * console's game list once and cached on the device for a week.
 */
class Catalog(private val context: Context) {
    private val file get() = File(context.cacheDir, "catalog-v1.json")

    /** Loads from the cache, or downloads when it's missing, old or [force]d. */
    suspend fun load(api: RaApi, force: Boolean, onProgress: (done: Int, total: Int) -> Unit): List<ConsoleGame> {
        if (!force) readCache()?.let { return it }
        val consoles = api.consoles()
        var done = 0
        var failed = 0
        onProgress(0, consoles.size)
        val gate = Semaphore(4)
        val lists = coroutineScope {
            consoles.map { c ->
                async {
                    val games = gate.withPermit {
                        runCatching { api.consoleGames(c.id, c.name) }.getOrElse { failed++; emptyList() }
                    }
                    done++
                    onProgress(done, consoles.size)
                    games
                }
            }.awaitAll()
        }
        val all = lists.flatten().distinctBy { it.id }.sortedBy { it.title.lowercase() }
        // Only keep a complete catalog, so a hiccup doesn't hide games for a week.
        if (failed == 0 && all.isNotEmpty()) writeCache(all)
        return all
    }

    fun clear() {
        file.delete()
    }

    private suspend fun readCache(): List<ConsoleGame>? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val root = JSONObject(file.readText())
            if (System.currentTimeMillis() - root.optLong("t") > MAX_AGE_MS) return@runCatching null
            val arr = root.getJSONArray("games")
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                ConsoleGame(
                    id = j.optInt("i"),
                    title = j.optString("t"),
                    iconUrl = j.optString("g").ifEmpty { null },
                    achievements = j.optInt("a"),
                    points = j.optInt("p"),
                    console = j.optString("c"),
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private suspend fun writeCache(games: List<ConsoleGame>) = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            games.forEach { g ->
                arr.put(
                    JSONObject()
                        .put("i", g.id).put("t", g.title).put("g", g.iconUrl.orEmpty())
                        .put("a", g.achievements).put("p", g.points).put("c", g.console)
                )
            }
            file.writeText(JSONObject().put("t", System.currentTimeMillis()).put("games", arr).toString())
        }
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
