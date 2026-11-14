package app.wasabi.ui

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wasabi.data.GameDetail
import app.wasabi.data.LibraryGame
import app.wasabi.data.Prefs
import app.wasabi.data.Profile
import app.wasabi.data.RaApi
import app.wasabi.data.RecentGame
import app.wasabi.data.Unlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class Game(val id: Int, val title: String) : Screen
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

enum class HomeTab(val label: String) { Playing("Playing"), Library("Library"), Unlocks("Unlocks"), Profile("Profile") }
enum class LibrarySort(val label: String) { Recent("Recent"), Progress("Progress"), Name("A–Z") }
enum class AchFilter(val label: String) { All("All"), Locked("Locked"), Unlocked("Unlocked") }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private var api: RaApi? = null
    private val jobs = mutableMapOf<String, Job>()

    val stack = mutableStateListOf<Screen>()
    val screen: Screen get() = stack.lastOrNull() ?: Screen.Login

    // Login
    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    val savedUser: String get() = prefs.user.orEmpty()

    // Home
    var profile by mutableStateOf<Load<Profile>>(Load.Loading); private set
    var tab by mutableStateOf(HomeTab.Playing); private set
    var recent by mutableStateOf<Load<List<RecentGame>>?>(null); private set
    var library by mutableStateOf<Load<List<LibraryGame>>?>(null); private set
    var unlocks by mutableStateOf<Load<List<Unlock>>?>(null); private set
    var librarySort by mutableStateOf(LibrarySort.Recent); private set
    private val listStates = HomeTab.entries.associateWith { LazyGridState() }
    fun listState(t: HomeTab) = listStates.getValue(t)

    // Game
    var game by mutableStateOf<Load<GameDetail>?>(null); private set
    var achFilter by mutableStateOf(AchFilter.All); private set
    val gameListState = LazyGridState()

    // D-pad focus: the key of the row that should grab focus next, and the
    // last focused row per list, so going back lands where you left off.
    var focusTarget by mutableStateOf<String?>(null)
    private val lastFocus = mutableMapOf<String, String>()

    init {
        val u = prefs.user
        val k = prefs.key
        if (u != null && k != null) {
            api = RaApi(u, k)
            stack.add(Screen.Home)
            loadProfile()
            ensureTab(tab)
        } else {
            stack.add(Screen.Login)
        }
    }

    // ---- login ----

    fun login(user: String, key: String) {
        val u = user.trim()
        val k = key.trim()
        if (u.isEmpty() || k.isEmpty()) {
            loginError = "Enter your username and Web API key."
            return
        }
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            try {
                val a = RaApi(u, k)
                val p = a.profile()
                prefs.save(p.user, k)
                api = RaApi(p.user, k)
                profile = Load.Ok(p)
                recent = null; library = null; unlocks = null
                tab = HomeTab.Playing
                stack.clear()
                stack.add(Screen.Home)
                ensureTab(tab)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = RaApi.friendly(e)
            } finally {
                loginBusy = false
            }
        }
    }

    fun signOut() {
        jobs.values.forEach { it.cancel() }
        prefs.clearKey()
        api = null
        profile = Load.Loading
        recent = null; library = null; unlocks = null; game = null
        lastFocus.clear()
        stack.clear()
        stack.add(Screen.Login)
    }

    // ---- navigation & controller actions ----

    fun selectTab(t: HomeTab) {
        if (t == tab) return
        tab = t
        ensureTab(t)
    }

    fun cycleTab(delta: Int) {
        if (screen != Screen.Home) return
        val all = HomeTab.entries
        selectTab(all[(tab.ordinal + delta + all.size) % all.size])
    }

    fun openGame(id: Int, title: String) {
        if (id <= 0) return
        stack.add(Screen.Game(id, title))
        achFilter = AchFilter.All
        game = null
        viewModelScope.launch { gameListState.scrollToItem(0) }
        loadGame(id)
    }

    /** Returns true when a screen was popped. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        jobs["game"]?.cancel()
        game = null
        if (screen == Screen.Home) restoreFocus(tab)
        return true
    }

    fun refresh() {
        when (val s = screen) {
            Screen.Home -> { loadTab(tab); if (tab != HomeTab.Profile) loadProfile() }
            is Screen.Game -> loadGame(s.id)
            Screen.Login -> Unit
        }
    }

    /** The Y button: sort the library, or filter a game's achievements. */
    fun option() {
        when (screen) {
            Screen.Home -> if (tab == HomeTab.Library) {
                librarySort = LibrarySort.entries[(librarySort.ordinal + 1) % LibrarySort.entries.size]
                viewModelScope.launch { listState(HomeTab.Library).scrollToItem(0) }
                focusTarget = sortedLibrary().firstOrNull()?.let { "g:${it.id}" }
            }
            is Screen.Game -> {
                achFilter = AchFilter.entries[(achFilter.ordinal + 1) % AchFilter.entries.size]
                viewModelScope.launch { gameListState.scrollToItem(0) }
                focusTarget = filteredAchievements().firstOrNull()?.let { "a:${it.id}" }
            }
            Screen.Login -> Unit
        }
    }

    fun rememberFocus(key: String) {
        lastFocus[focusScope()] = key
    }

    fun consumeFocus(key: String) {
        if (focusTarget == key) focusTarget = null
    }

    // ---- derived lists ----

    fun sortedLibrary(): List<LibraryGame> {
        val list = (library as? Load.Ok)?.data.orEmpty()
        return when (librarySort) {
            LibrarySort.Recent -> list.sortedByDescending { it.lastUnlock.orEmpty() }
            LibrarySort.Progress -> list.sortedWith(compareByDescending<LibraryGame> { it.fraction }.thenByDescending { it.shown })
            LibrarySort.Name -> list.sortedBy { it.title.lowercase() }
        }
    }

    fun filteredAchievements() = (game as? Load.Ok)?.data?.achievements.orEmpty().filter {
        when (achFilter) {
            AchFilter.All -> true
            AchFilter.Locked -> !it.unlocked
            AchFilter.Unlocked -> it.unlocked
        }
    }

    // ---- loading ----

    private fun focusScope() = when (val s = screen) {
        is Screen.Game -> "game:${s.id}"
        else -> tab.name
    }

    private fun keysFor(t: HomeTab): List<String> = when (t) {
        HomeTab.Playing -> (recent as? Load.Ok)?.data.orEmpty().map { "g:${it.id}" }
        HomeTab.Library -> sortedLibrary().map { "g:${it.id}" }
        HomeTab.Unlocks -> (unlocks as? Load.Ok)?.data.orEmpty().map { "u:${it.achievementId}:${it.date}" }
        HomeTab.Profile -> listOf("signout")
    }

    private fun restoreFocus(t: HomeTab) {
        if (screen != Screen.Home || tab != t) return
        val keys = keysFor(t)
        val last = lastFocus[t.name]
        focusTarget = if (last != null && last in keys) last else keys.firstOrNull()
    }

    private fun ensureTab(t: HomeTab) {
        val state = when (t) {
            HomeTab.Playing -> recent
            HomeTab.Library -> library
            HomeTab.Unlocks -> unlocks
            HomeTab.Profile -> profile
        }
        if (state == null || state is Load.Err) loadTab(t) else restoreFocus(t)
    }

    private fun loadTab(t: HomeTab) = when (t) {
        HomeTab.Playing -> launchLoad("recent", { recent = it }, { restoreFocus(t) }) { recentGames() }
        HomeTab.Library -> launchLoad("library", { library = it }, { restoreFocus(t) }) { library() }
        HomeTab.Unlocks -> launchLoad("unlocks", { unlocks = it }, { restoreFocus(t) }) { recentUnlocks() }
        HomeTab.Profile -> loadProfile()
    }

    private fun loadProfile() = launchLoad("profile", { profile = it }) { profile() }

    private fun loadGame(id: Int) = launchLoad("game", { game = it }, { g ->
        focusTarget = g.achievements.firstOrNull()?.let { "a:${it.id}" }
    }) { game(id) }

    private fun <T> launchLoad(
        slot: String,
        set: (Load<T>) -> Unit,
        onOk: (T) -> Unit = {},
        block: suspend RaApi.() -> T,
    ) {
        val a = api ?: return
        jobs[slot]?.cancel()
        set(Load.Loading)
        jobs[slot] = viewModelScope.launch {
            val result: Load<T> = try {
                Load.Ok(a.block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Err(RaApi.friendly(e))
            }
            set(result)
            if (result is Load.Ok) onOk(result.data)
        }
    }
}
