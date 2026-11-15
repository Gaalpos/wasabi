package app.wasabi.ui

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wasabi.data.Console
import app.wasabi.data.ConsoleGame
import app.wasabi.data.FollowedUser
import app.wasabi.data.GameDetail
import app.wasabi.data.LibraryGame
import app.wasabi.data.Prefs
import app.wasabi.data.Profile
import app.wasabi.data.RaApi
import app.wasabi.data.RecentGame
import app.wasabi.data.Suggestions
import app.wasabi.data.Unlock
import app.wasabi.data.UserView
import app.wasabi.data.WantGame
import app.wasabi.data.suggestions
import app.wasabi.data.userView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    /** A game's achievements, with the progress of [user] (null = you). */
    data class Game(val id: Int, val title: String, val user: String? = null) : Screen
    /** Someone you follow. */
    data class User(val name: String) : Screen
    /** All games with achievements on one console. */
    data class ConsoleGames(val id: Int, val name: String) : Screen
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

enum class HomeTab(val label: String) {
    Playing("Playing"),
    ForYou("For You"),
    WantToPlay("Want to Play"),
    Library("Library"),
    Unlocks("Unlocks"),
    Following("Following"),
    Browse("Browse"),
    Profile("Profile"),
}
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
    var wantToPlay by mutableStateOf<Load<List<WantGame>>?>(null); private set
    var suggested by mutableStateOf<Load<Suggestions>?>(null); private set
    var following by mutableStateOf<Load<List<FollowedUser>>?>(null); private set
    var consoles by mutableStateOf<Load<List<Console>>?>(null); private set
    var librarySort by mutableStateOf(LibrarySort.Recent); private set
    private val listStates = HomeTab.entries.associateWith { LazyGridState() }
    fun listState(t: HomeTab) = listStates.getValue(t)

    // Game
    var game by mutableStateOf<Load<GameDetail>?>(null); private set
    var achFilter by mutableStateOf(AchFilter.All); private set
    val gameListState = LazyGridState()

    // Another user's page
    var user by mutableStateOf<Load<UserView>?>(null); private set
    val userListState = LazyGridState()

    // One console's game list
    var consoleGames by mutableStateOf<Load<List<ConsoleGame>>?>(null); private set
    var consoleQuery by mutableStateOf("")
    val consoleListState = LazyGridState()

    // D-pad focus: the key of the row that should grab focus next, and the
    // last focused row per list, so going back lands where you left off.
    var focusTarget by mutableStateOf<String?>(null)
    private val lastFocus = mutableMapOf<String, String>()

    /** True when [name] is the signed-in user. */
    fun isMe(name: String?) = name == null || name.equals(api?.user, ignoreCase = true)

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
                clearLists()
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
        clearLists()
        lastFocus.clear()
        stack.clear()
        stack.add(Screen.Login)
    }

    private fun clearLists() {
        recent = null; library = null; unlocks = null; wantToPlay = null
        suggested = null; following = null; consoles = null
        game = null; user = null; consoleGames = null
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

    /** Opens a game; [of] shows someone else's progress in it. */
    fun openGame(id: Int, title: String, of: String? = null) {
        if (id <= 0) return
        val s = Screen.Game(id, title, of?.takeUnless { isMe(it) })
        stack.add(s)
        achFilter = AchFilter.All
        game = null
        viewModelScope.launch { gameListState.scrollToItem(0) }
        loadGame(s)
    }

    fun openUser(name: String) {
        if (name.isBlank()) return
        if (isMe(name)) {
            // Your own page is the Profile tab.
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            selectTab(HomeTab.Profile)
            return
        }
        stack.add(Screen.User(name))
        user = null
        viewModelScope.launch { userListState.scrollToItem(0) }
        loadUser(name)
    }

    fun openConsole(c: Console) {
        stack.add(Screen.ConsoleGames(c.id, c.name))
        consoleGames = null
        consoleQuery = ""
        viewModelScope.launch { consoleListState.scrollToItem(0) }
        loadConsole(c.id)
    }

    /** Returns true when a screen was popped. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        when (stack.removeAt(stack.lastIndex)) {
            is Screen.Game -> { jobs["game"]?.cancel(); game = null }
            is Screen.User -> { jobs["user"]?.cancel(); user = null }
            is Screen.ConsoleGames -> { jobs["console"]?.cancel(); consoleGames = null }
            else -> Unit
        }
        restoreFocusHere()
        return true
    }

    fun refresh() {
        when (val s = screen) {
            Screen.Home -> { loadTab(tab); if (tab != HomeTab.Profile) loadProfile() }
            is Screen.Game -> loadGame(s)
            is Screen.User -> loadUser(s.name)
            is Screen.ConsoleGames -> loadConsole(s.id)
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
            else -> Unit
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

    /** Console games matching the search box (title words in any order). */
    fun filteredConsoleGames(): List<ConsoleGame> {
        val all = (consoleGames as? Load.Ok)?.data.orEmpty()
        val words = consoleQuery.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { g -> val t = g.title.lowercase(); words.all { it in t } }
    }

    // ---- focus bookkeeping ----

    private fun focusScope() = when (val s = screen) {
        is Screen.Game -> "game:${s.id}:${s.user}"
        is Screen.User -> "user:${s.name}"
        is Screen.ConsoleGames -> "console:${s.id}"
        else -> tab.name
    }

    private fun keysFor(t: HomeTab): List<String> = when (t) {
        HomeTab.Playing -> (recent as? Load.Ok)?.data.orEmpty().map { "g:${it.id}" }
        HomeTab.ForYou -> (suggested as? Load.Ok)?.data?.let { s ->
            s.almostDone.map { "s:a:${it.id}" } + s.friendsPlaying.map { "s:f:${it.game.id}" } + s.fromWantToPlay.map { "s:w:${it.id}" }
        }.orEmpty()
        HomeTab.WantToPlay -> (wantToPlay as? Load.Ok)?.data.orEmpty().map { "w:${it.id}" }
        HomeTab.Library -> sortedLibrary().map { "g:${it.id}" }
        HomeTab.Unlocks -> (unlocks as? Load.Ok)?.data.orEmpty().map { "u:${it.achievementId}:${it.date}" }
        HomeTab.Following -> (following as? Load.Ok)?.data.orEmpty().map { "f:${it.user}" }
        HomeTab.Browse -> (consoles as? Load.Ok)?.data.orEmpty().map { "c:${it.id}" }
        HomeTab.Profile -> listOf("signout")
    }

    private fun keysHere(): List<String> = when (val s = screen) {
        Screen.Home -> keysFor(tab)
        is Screen.Game -> filteredAchievements().map { "a:${it.id}" }
        is Screen.User -> (user as? Load.Ok)?.data?.let { v ->
            v.recentGames.map { "ug:${it.id}" } + v.unlocks.map { "uu:${it.achievementId}:${it.date}" }
        }.orEmpty()
        is Screen.ConsoleGames -> filteredConsoleGames().map { "cg:${it.id}" }
        Screen.Login -> emptyList()
    }

    /** Focus the row you last used on this screen, or its first row. */
    private fun restoreFocusHere() {
        val keys = keysHere()
        val last = lastFocus[focusScope()]
        focusTarget = if (last != null && last in keys) last else keys.firstOrNull()
    }

    private fun restoreFocus(t: HomeTab) {
        if (screen == Screen.Home && tab == t) restoreFocusHere()
    }

    // ---- loading ----

    private fun ensureTab(t: HomeTab) {
        val state = when (t) {
            HomeTab.Playing -> recent
            HomeTab.ForYou -> suggested
            HomeTab.WantToPlay -> wantToPlay
            HomeTab.Library -> library
            HomeTab.Unlocks -> unlocks
            HomeTab.Following -> following
            HomeTab.Browse -> consoles
            HomeTab.Profile -> profile
        }
        if (state == null || state is Load.Err) loadTab(t) else restoreFocus(t)
    }

    private fun loadTab(t: HomeTab) = when (t) {
        HomeTab.Playing -> launchLoad("recent", { recent = it }, { restoreFocus(t) }) { recentGames() }
        HomeTab.ForYou -> launchLoad("suggested", { suggested = it }, { restoreFocus(t) }) { suggestions() }
        HomeTab.WantToPlay -> launchLoad("want", { wantToPlay = it }, { restoreFocus(t) }) { wantToPlay() }
        HomeTab.Library -> launchLoad("library", { library = it }, { restoreFocus(t) }) { library() }
        HomeTab.Unlocks -> launchLoad("unlocks", { unlocks = it }, { restoreFocus(t) }) { recentUnlocks() }
        HomeTab.Following -> launchLoad("following", { following = it }, { restoreFocus(t) }) { following() }
        HomeTab.Browse -> launchLoad("consoles", { consoles = it }, { restoreFocus(t) }) { consoles() }
        HomeTab.Profile -> loadProfile()
    }

    private fun loadProfile() = launchLoad("profile", { profile = it }) { profile() }

    private fun loadGame(s: Screen.Game) = launchLoad("game", { game = it }, { restoreFocusHere() }) {
        if (s.user != null) game(s.id, s.user) else game(s.id)
    }

    private fun loadUser(name: String) = launchLoad("user", { user = it }, { restoreFocusHere() }) { userView(name) }

    private fun loadConsole(id: Int) = launchLoad("console", { consoleGames = it }, { restoreFocusHere() }) { consoleGames(id) }

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
