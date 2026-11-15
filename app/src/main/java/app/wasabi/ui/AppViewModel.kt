package app.wasabi.ui

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.wasabi.data.Catalog
import app.wasabi.data.CompareEntry
import app.wasabi.data.Console
import app.wasabi.data.ConsoleGame
import app.wasabi.data.FollowedUser
import app.wasabi.data.GameExtras
import app.wasabi.data.GameDetail
import app.wasabi.data.LibraryGame
import app.wasabi.data.Prefs
import app.wasabi.data.Profile
import app.wasabi.data.RaApi
import app.wasabi.data.RaWeb
import app.wasabi.data.RecentGame
import app.wasabi.data.RelatedGame
import app.wasabi.data.compare
import app.wasabi.data.Suggestions
import app.wasabi.data.Unlock
import app.wasabi.data.UpdateInfo
import app.wasabi.data.Updater
import app.wasabi.data.UserView
import app.wasabi.data.WantGame
import app.wasabi.data.suggestions
import app.wasabi.data.userView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    /** A game's achievements, with the progress of [user] (null = you). */
    data class Game(val id: Int, val title: String, val user: String? = null, val key: Long = System.nanoTime()) : Screen
    /** Someone you follow. */
    data class User(val name: String) : Screen
    /** All games with achievements on one console. */
    data class ConsoleGames(val id: Int, val name: String) : Screen
    /** One-time sign-in on retroachievements.org, for Want to Play. */
    data object WebSignIn : Screen
    /** A game's images full screen, as (url, label), starting at [index]. */
    data class Images(val images: List<Pair<String, String>>, val index: Int) : Screen
}

/** One add/remove request for the hidden website session. */
data class WebJob(val gameId: Int, val add: Boolean, val nonce: String = System.nanoTime().toString())

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
    Search("Search"),
    Profile("Profile"),
    /** Always shown, last in the strip; Menu (Start) jumps here. */
    Settings("Settings"),
}
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

enum class LibrarySort(val label: String) { Recent("Recent"), Progress("Progress"), Name("A–Z") }
/** Tabs on a game's screen. */
enum class GameTab(val label: String) {
    Achievements("Achievements"),
    Info("Info"),
    Series("Series"),
    Similar("Similar"),
    Compare("Compare"),
}

/** Everything one game screen shows; each open game screen has its own. */
class GameState {
    var detail by mutableStateOf<Load<GameDetail>>(Load.Loading)
    var tab by mutableStateOf(GameTab.Achievements)
    var filter by mutableStateOf(AchFilter.All)
    var extras by mutableStateOf<Load<GameExtras>?>(null)
    var series by mutableStateOf<Load<List<RelatedGame>>?>(null)
    var compare by mutableStateOf<Load<List<CompareEntry>>?>(null)
    val listStates = GameTab.entries.associateWith { LazyGridState() }
}

/** A website page to read in the hidden browser. */
data class PageJob(val url: String, val apiKey: String, val nonce: String = System.nanoTime().toString())

enum class AchFilter(val label: String) { All("All"), Locked("Locked"), Unlocked("Unlocked"), Missable("Missable") }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val catalogStore = Catalog(app)
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

    // Search: every game across all consoles, filtered by [searchQuery].
    var catalog by mutableStateOf<Load<List<ConsoleGame>>?>(null); private set
    var catalogProgress by mutableStateOf<Pair<Int, Int>?>(null); private set
    var searchQuery by mutableStateOf("")

    // Settings
    var enabledTabs by mutableStateOf(HomeTab.entries.toSet()); private set
    val visibleTabs: List<HomeTab> get() = HomeTab.entries.filter { it in enabledTabs || it == HomeTab.Settings }
    /** Games hidden from Playing, and the last one hidden (for Undo). */
    var hiddenPlaying by mutableStateOf(prefs.hiddenPlaying); private set   // id -> last played when hidden
    var lastHidden by mutableStateOf<RecentGame?>(null); private set

    /** Favorite systems, listed first in Browse. */
    var favoriteConsoles by mutableStateOf(prefs.favoriteConsoles); private set

    /** Last tab-strip direction: +1 moving right, -1 moving left. */
    var tabDirection by mutableStateOf(1); private set

    // In-app updates from GitHub releases.
    val installedVersion: String = Updater.installedVersion(app)
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    /** Bumped to ask the UI to open Android's installer for the downloaded APK. */
    var installRequest by mutableStateOf(0); private set
    private var installAfterResume = false
    private var updateDismissed by mutableStateOf(false)
    val showUpdateBanner: Boolean
        get() = !updateDismissed && update.let { it is UpdateState.Available || it is UpdateState.Downloading || it is UpdateState.Ready }

    // Want to Play edits through the website session (see RaWeb).
    var webSignedIn by mutableStateOf(prefs.webSignedIn); private set
    /** The request the hidden WebView should run now, if any. */
    var webJob by mutableStateOf<WebJob?>(null); private set
    /** Game whose Want to Play change is in flight. */
    var wantBusy by mutableStateOf<Int?>(null); private set
    var wantError by mutableStateOf<String?>(null); private set
    private var afterSignIn: WebJob? = null

    /** Set when the RA site was opened to edit Want to Play; refresh on return. */
    private var wantToPlayEdited = false
    private val listStates = HomeTab.entries.associateWith { LazyGridState() }
    fun listState(t: HomeTab) = listStates.getValue(t)

    // Game
    private val gameStates = mutableMapOf<Screen.Game, GameState>()
    fun gameState(s: Screen.Game): GameState = gameStates.getOrPut(s) { GameState() }
    private val currentGame: GameState? get() = (screen as? Screen.Game)?.let { gameState(it) }
    val game: Load<GameDetail>? get() = currentGame?.detail
    val achFilter: AchFilter get() = currentGame?.filter ?: AchFilter.All

    /** Website page the hidden browser should read now (series, similar games). */
    var pageJob by mutableStateOf<PageJob?>(null); private set
    private val pageCallbacks = mutableMapOf<String, (String?) -> Unit>()

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
        Palette.accent = AccentColor.entries.find { it.name == prefs.accent } ?: AccentColor.Wasabi
        prefs.tabs?.let { saved ->
            val tabs = saved.split(',').mapNotNull { n -> HomeTab.entries.find { it.name == n } }.toSet()
            if (tabs.isNotEmpty()) enabledTabs = tabs
        }
        tab = visibleTabs.first()
        val u = prefs.user
        val k = prefs.key
        if (u != null && k != null) {
            api = RaApi(u, k)
            stack.add(Screen.Home)
            loadProfile()
            ensureTab(tab)
            checkForUpdates(manual = false)
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
                tab = visibleTabs.first()
                stack.clear()
                stack.add(Screen.Home)
                ensureTab(tab)
                checkForUpdates(manual = false)
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
        webSignOut()
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
        suggested = null; following = null; consoles = null; catalog = null
        gameStates.clear(); user = null; consoleGames = null
    }

    // ---- navigation & controller actions ----

    fun selectTab(t: HomeTab) {
        if (t == tab) return
        lastHidden = null
        val all = visibleTabs
        tabDirection = if (all.indexOf(t) >= all.indexOf(tab)) 1 else -1
        tab = t
        ensureTab(t)
    }

    fun cycleTab(delta: Int) {
        val s = screen
        if (s is Screen.Game) {
            val all = GameTab.entries
            val st = gameState(s)
            selectGameTab(all[(st.tab.ordinal + delta + all.size) % all.size])
            return
        }
        if (s != Screen.Home) return
        val all = visibleTabs
        val i = all.indexOf(tab).coerceAtLeast(0)
        selectTab(all[(i + delta + all.size) % all.size])
        tabDirection = if (delta >= 0) 1 else -1
    }

    // ---- game tabs ----

    fun selectGameTab(t: GameTab) {
        val s = screen as? Screen.Game ?: return
        val st = gameState(s)
        if (st.tab == t) return
        st.tab = t
        when (t) {
            GameTab.Series -> if (st.extras !is Load.Ok) loadExtras(s)
            GameTab.Similar -> if (st.extras !is Load.Ok) loadExtras(s)
            GameTab.Compare -> if (st.compare == null || st.compare is Load.Err) loadCompare(s)
            else -> Unit
        }
        restoreFocusHere(preferContent = true)
    }

    /** Series hub, other hubs and similar games, read from the website's game page. */
    private fun loadExtras(s: Screen.Game) {
        val st = gameState(s)
        val key = api?.webApiKey ?: return
        st.extras = Load.Loading
        if (st.tab == GameTab.Series) st.series = Load.Loading
        val job = PageJob("${RaWeb.ORIGIN}/game/${s.id}", key)
        pageCallbacks[job.nonce] = { json ->
            st.extras = runCatching { Load.Ok(RaApi.parseExtras(json!!)) }
                .getOrElse { Load.Err("Couldn't read this game's page on retroachievements.org.") }
            when (val e = st.extras) {
                is Load.Ok -> st.series = when {
                    e.data.series == null -> Load.Ok(emptyList())
                    e.data.seriesGames != null -> Load.Ok(e.data.seriesGames)
                    e.data.seriesStatus == 401 -> Load.Err("RetroAchievements didn't accept the Web API key for the series list.")
                    else -> Load.Err("Couldn't load the series' games (error ${e.data.seriesStatus ?: "unknown"}). Press X to retry.")
                }
                is Load.Err -> st.series = Load.Err(e.message)
                else -> Unit
            }
            if (screen == s) restoreFocusHere(preferContent = true)
        }
        pageJob = job
        viewModelScope.launch {
            delay(30_000)
            if (pageCallbacks.containsKey(job.nonce)) onPageData(job.nonce, null)
        }
    }

    /** Result from the hidden browser's page read (null or "" on failure). */
    fun onPageData(nonce: String, json: String?) {
        if (pageJob?.nonce == nonce) pageJob = null
        pageCallbacks.remove(nonce)?.invoke(json?.takeIf { it.isNotBlank() })
    }

    private fun loadCompare(s: Screen.Game) {
        val st = gameState(s)
        launchLoad("compare:${s.key}", { st.compare = it }, { if (screen == s) restoreFocusHere(preferContent = true) }) {
            val total = ((st.detail as? Load.Ok)?.data?.total) ?: 0
            compare(s.id, total)
        }
    }

    // ---- settings ----

    /** Menu (Start): back to the home screen's Settings tab. */
    fun openSettings() {
        if (screen == Screen.Login) return
        while (stack.size > 1) back()
        selectTab(HomeTab.Settings)
        focusTarget = "upd"
    }

    fun setAccent(c: AccentColor) {
        Palette.accent = c
        prefs.accent = c.name
    }

    /** Turns a home tab on or off. At least one tab always stays on. */
    fun toggleTab(t: HomeTab) {
        if (t == HomeTab.Settings) return
        val next = if (t in enabledTabs) enabledTabs - t else enabledTabs + t
        if (next.none { it != HomeTab.Settings }) return
        enabledTabs = next
        prefs.tabs = HomeTab.entries.filter { it in next }.joinToString(",") { it.name }
        if (tab !in next) {
            tab = visibleTabs.first()
            ensureTab(tab)
        }
    }

    // ---- want to play ----

    /** Whether game [id] is on your Want to Play list, or null if unknown. */
    fun isWanted(id: Int): Boolean? = (wantToPlay as? Load.Ok)?.data?.any { it.id == id }

    /** Adds the game to Want to Play, or removes it if it's already there. */
    fun toggleWantToPlay(gameId: Int) {
        if (wantBusy != null) return
        wantError = null
        val job = WebJob(gameId, add = isWanted(gameId) != true)
        if (!webSignedIn) {
            afterSignIn = job
            stack.add(Screen.WebSignIn)
            return
        }
        runWebJob(job)
    }

    private fun runWebJob(job: WebJob) {
        wantBusy = job.gameId
        webJob = job
        viewModelScope.launch {
            delay(25_000)
            if (webJob?.nonce == job.nonce) onWebResult(job.nonce, -2)
        }
    }

    /** Result from the hidden WebView (status code, or -1/-2 for network/timeout). */
    fun onWebResult(nonce: String, status: Int) {
        val job = webJob?.takeIf { it.nonce == nonce } ?: return
        webJob = null
        wantBusy = null
        when {
            status in 200..299 -> applyWantChange(job)
            status == 401 || status == 419 -> {
                // The website session ended: sign in again, then retry.
                saveWebSignedIn(false)
                afterSignIn = job.copy(nonce = System.nanoTime().toString())
                if (screen != Screen.WebSignIn) stack.add(Screen.WebSignIn)
            }
            status == -2 -> wantError = "retroachievements.org took too long. Try again."
            status < 0 -> wantError = "Couldn't reach retroachievements.org."
            else -> wantError = "Couldn't update Want to Play (error $status)."
        }
    }

    /** Update the local list right away instead of waiting for a reload. */
    private fun applyWantChange(job: WebJob) {
        val list = (wantToPlay as? Load.Ok)?.data.orEmpty()
        val g = gameStates.values.firstNotNullOfOrNull { (it.detail as? Load.Ok)?.data?.takeIf { d -> d.id == job.gameId } }
        wantToPlay = Load.Ok(
            if (job.add) {
                if (list.any { it.id == job.gameId } || g == null) list
                else listOf(WantGame(g.id, g.title, g.console, g.iconUrl, g.total, g.points)) + list
            } else {
                list.filterNot { it.id == job.gameId }
            }
        )
    }

    /** The sign-in page reached a signed-in page. */
    fun onWebSignedIn() {
        saveWebSignedIn(true)
        if (screen == Screen.WebSignIn) {
            stack.removeAt(stack.lastIndex)
            restoreFocusHere()
        }
        afterSignIn?.let { runWebJob(it) }
        afterSignIn = null
    }

    fun openWebSignIn() {
        if (screen != Screen.WebSignIn) stack.add(Screen.WebSignIn)
    }

    fun webSignOut() {
        RaWeb.signOut()
        saveWebSignedIn(false)
    }

    private fun saveWebSignedIn(v: Boolean) {
        webSignedIn = v
        prefs.webSignedIn = v
    }

    /** Called before opening the game's RetroAchievements page to edit the list. */
    fun editingWantToPlay() {
        wantToPlayEdited = true
    }

    // ---- updates ----

    /** Looks for a newer release. Quiet unless [manual] (then errors show). */
    fun checkForUpdates(manual: Boolean) {
        val busy = update is UpdateState.Checking || update is UpdateState.Downloading
        if (busy) return
        update = UpdateState.Checking
        if (manual) updateDismissed = false
        viewModelScope.launch {
            update = try {
                Updater.check(installedVersion)?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) UpdateState.Failed(RaApi.friendly(e)) else UpdateState.Idle
            }
        }
    }

    /** Downloads the available update, then asks the UI to install it. */
    fun startUpdate() {
        when (val u = update) {
            is UpdateState.Ready -> installRequest++
            is UpdateState.Available -> viewModelScope.launch {
                update = UpdateState.Downloading(u.info, 0f)
                update = try {
                    val apk = Updater.download(getApplication<Application>(), u.info) { p ->
                        update = UpdateState.Downloading(u.info, p)
                    }
                    installRequest++
                    UpdateState.Ready(u.info, apk)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    UpdateState.Failed(RaApi.friendly(e))
                }
            }
            else -> Unit
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    /** Android needs "install unknown apps" first; retry when the user returns. */
    fun installBlocked() {
        installAfterResume = true
    }

    /** Back from the browser or a settings page. */
    fun onResume() {
        if (installAfterResume && update is UpdateState.Ready) {
            installAfterResume = false
            installRequest++
        }
        if (!wantToPlayEdited) return
        wantToPlayEdited = false
        loadTab(HomeTab.WantToPlay)
        if (suggested != null) loadTab(HomeTab.ForYou)
    }

    // ---- search ----

    /** Catalog games whose title contains every typed word (max 300 shown). */
    fun searchResults(): List<ConsoleGame> {
        val all = (catalog as? Load.Ok)?.data.orEmpty()
        val words = searchQuery.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.asSequence().filter { g -> val t = g.title.lowercase(); words.all { it in t } }.take(300).toList()
    }

    fun searchMatchCount(): Int {
        val all = (catalog as? Load.Ok)?.data.orEmpty()
        val words = searchQuery.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0
        return all.count { g -> val t = g.title.lowercase(); words.all { it in t } }
    }

    /** Opens a game; [of] shows someone else's progress in it. */
    fun openGame(id: Int, title: String, of: String? = null) {
        if (id <= 0) return
        val s = Screen.Game(id, title, of?.takeUnless { isMe(it) })
        stack.add(s)
        gameState(s)
        loadGame(s)
        // So the game screen can say whether it's on your Want to Play list.
        if (wantToPlay == null) launchLoad("want", { wantToPlay = it }) { wantToPlay() }
    }

    fun openImages(images: List<Pair<String, String>>, index: Int) {
        if (images.isEmpty()) return
        stack.add(Screen.Images(images, index.coerceIn(0, images.lastIndex)))
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
        when (val popped = stack.removeAt(stack.lastIndex)) {
            is Screen.Game -> {
                listOf("game", "series", "compare").forEach { jobs["$it:${popped.key}"]?.cancel() }
                gameStates.remove(popped)
            }
            is Screen.User -> { jobs["user"]?.cancel(); user = null }
            is Screen.ConsoleGames -> { jobs["console"]?.cancel(); consoleGames = null }
            Screen.WebSignIn -> afterSignIn = null
            else -> Unit
        }
        restoreFocusHere()
        return true
    }

    fun refresh() {
        when (val s = screen) {
            Screen.Home -> { loadTab(tab); if (tab != HomeTab.Profile && tab != HomeTab.Settings) loadProfile() }
            is Screen.Game -> {
                loadGame(s)
                val st = gameState(s)
                when (st.tab) {
                    GameTab.Series, GameTab.Similar -> loadExtras(s)
                    GameTab.Compare -> loadCompare(s)
                    else -> Unit
                }
            }
            is Screen.User -> loadUser(s.name)
            is Screen.ConsoleGames -> loadConsole(s.id)
            is Screen.Images, Screen.WebSignIn, Screen.Login -> Unit
        }
    }

    /** The Y button: sort the library, or filter a game's achievements. */
    fun option() {
        when (screen) {
            Screen.Home -> if (tab == HomeTab.Playing) {
                lastFocus[HomeTab.Playing.name]?.removePrefix("g:")?.toIntOrNull()?.let { hideFromPlaying(it) }
            } else if (tab == HomeTab.Browse) {
                // Star or unstar the focused system; focus follows it to its new spot.
                lastFocus[HomeTab.Browse.name]?.removePrefix("c:")?.toIntOrNull()?.let {
                    toggleFavoriteConsole(it)
                    focusTarget = "c:$it"
                }
            } else if (tab == HomeTab.Library) {
                librarySort = LibrarySort.entries[(librarySort.ordinal + 1) % LibrarySort.entries.size]
                viewModelScope.launch { listState(HomeTab.Library).scrollToItem(0) }
                focusTarget = sortedLibrary().firstOrNull()?.let { "g:${it.id}" }
            }
            is Screen.ConsoleGames -> toggleFavoriteConsole((screen as Screen.ConsoleGames).id)
            is Screen.Game -> currentGame?.takeIf { it.tab == GameTab.Achievements }?.let { st ->
                st.filter = AchFilter.entries[(st.filter.ordinal + 1) % AchFilter.entries.size]
                viewModelScope.launch { st.listStates.getValue(GameTab.Achievements).scrollToItem(0) }
                focusTarget = filteredAchievements(st).firstOrNull()?.let { "a:${it.id}" } ?: "filter"
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

    fun filteredAchievements(st: GameState? = currentGame) = (st?.detail as? Load.Ok)?.data?.achievements.orEmpty().filter {
        when (st?.filter ?: AchFilter.All) {
            AchFilter.All -> true
            AchFilter.Locked -> !it.unlocked
            AchFilter.Unlocked -> it.unlocked
            AchFilter.Missable -> it.missable
        }
    }

    /** Recently played games, minus the ones hidden in Wasabi. */
    fun visibleRecent(): List<RecentGame> = (recent as? Load.Ok)?.data.orEmpty().filter { it.id !in hiddenPlaying.keys }

    /** Unhides games played again since they were hidden. */
    private fun reconcileHidden() {
        val list = (recent as? Load.Ok)?.data ?: return
        val m = hiddenPlaying.toMutableMap()
        var changed = false
        for (g in list) {
            val hiddenAt = m[g.id] ?: continue
            val played = g.lastPlayed.orEmpty()
            when {
                hiddenAt.isEmpty() -> { m[g.id] = played; changed = true }   // from older versions: start tracking
                played > hiddenAt -> { m.remove(g.id); changed = true }       // played again: show it
            }
        }
        if (changed) {
            hiddenPlaying = m
            prefs.hiddenPlaying = m
        }
    }

    /**
     * Hides a game from Playing (RetroAchievements has no way to remove a
     * recently played game short of resetting its progress). Focus moves to
     * the next game, and [lastHidden] offers Undo.
     */
    fun hideFromPlaying(id: Int) {
        val list = visibleRecent()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        lastHidden = list[i]
        hiddenPlaying = hiddenPlaying + (id to list[i].lastPlayed.orEmpty())
        prefs.hiddenPlaying = hiddenPlaying
        val next = list.getOrNull(i + 1) ?: list.getOrNull(i - 1)
        focusTarget = next?.let { "g:${it.id}" } ?: "undo"
    }

    fun undoHide() {
        val g = lastHidden ?: return
        hiddenPlaying = hiddenPlaying - g.id
        prefs.hiddenPlaying = hiddenPlaying
        lastHidden = null
        focusTarget = "g:${g.id}"
    }

    /** Brings every hidden game back to Playing. */
    fun showHiddenPlaying() {
        hiddenPlaying = emptyMap()
        prefs.hiddenPlaying = hiddenPlaying
        lastHidden = null
    }

    fun isFavoriteConsole(id: Int) = id in favoriteConsoles

    fun toggleFavoriteConsole(id: Int) {
        favoriteConsoles = if (id in favoriteConsoles) favoriteConsoles - id else favoriteConsoles + id
        prefs.favoriteConsoles = favoriteConsoles
    }

    /** Systems for Browse: favorites first, then the rest, each A–Z. */
    fun sortedConsoles(): Pair<List<Console>, List<Console>> =
        (consoles as? Load.Ok)?.data.orEmpty().partition { it.id in favoriteConsoles }

    /** Console games matching the search box (title words in any order). */
    fun filteredConsoleGames(): List<ConsoleGame> {
        val all = (consoleGames as? Load.Ok)?.data.orEmpty()
        val words = consoleQuery.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { g -> val t = g.title.lowercase(); words.all { it in t } }
    }

    // ---- focus bookkeeping ----

    private fun focusScope() = when (val s = screen) {
        is Screen.Game -> "game:${s.key}:${gameState(s).tab}"
        is Screen.User -> "user:${s.name}"
        is Screen.ConsoleGames -> "console:${s.id}"
        else -> tab.name
    }

    private fun keysFor(t: HomeTab): List<String> = when (t) {
        HomeTab.Playing -> visibleRecent().map { "g:${it.id}" }
        HomeTab.ForYou -> (suggested as? Load.Ok)?.data?.let { s ->
            s.almostDone.map { "s:a:${it.id}" } + s.friendsPlaying.map { "s:f:${it.game.id}" } + s.fromWantToPlay.map { "s:w:${it.id}" }
        }.orEmpty()
        HomeTab.WantToPlay -> (wantToPlay as? Load.Ok)?.data.orEmpty().map { "w:${it.id}" }
        HomeTab.Library -> sortedLibrary().map { "g:${it.id}" }
        HomeTab.Unlocks -> (unlocks as? Load.Ok)?.data.orEmpty().map { "u:${it.achievementId}:${it.date}" }
        HomeTab.Following -> (following as? Load.Ok)?.data.orEmpty().map { "f:${it.user}" }
        HomeTab.Browse -> sortedConsoles().let { (fav, rest) -> (fav + rest).map { "c:${it.id}" } }
        HomeTab.Search -> searchResults().map { "sg:${it.id}" }
        HomeTab.Profile -> listOf("signout")
        HomeTab.Settings -> listOf("upd", "web") + (if (hiddenPlaying.isNotEmpty()) listOf("hidden") else emptyList()) + AccentColor.entries.map { "ac:${it.name}" } +
            HomeTab.entries.filter { it != HomeTab.Settings }.map { "tab:${it.name}" }
    }

    private fun keysHere(): List<String> = when (val s = screen) {
        Screen.Home -> keysFor(tab)
        // Want to Play first (focused when the game opens), then the tab's content.
        is Screen.Game -> {
            val st = gameState(s)
            val g = (st.detail as? Load.Ok)?.data
            listOf("want") + when (st.tab) {
                GameTab.Achievements -> listOf("filter") + filteredAchievements(st).map { "a:${it.id}" }
                GameTab.Info -> g?.let { d -> d.images.indices.map { "img:$it" } + gameFacts(d).indices.map { "fact:$it" } }.orEmpty()
                GameTab.Series -> (st.series as? Load.Ok)?.data.orEmpty().map { "ser:${it.id}" } +
                    (st.extras as? Load.Ok)?.data?.hubs.orEmpty().map { "hub:${it.id}" }
                GameTab.Similar -> (st.extras as? Load.Ok)?.data?.similar.orEmpty().map { "sim:${it.id}" }
                GameTab.Compare -> (st.compare as? Load.Ok)?.data.orEmpty().map { "cmp:${it.user}" }
            }
        }
        is Screen.User -> (user as? Load.Ok)?.data?.let { v ->
            v.recentGames.map { "ug:${it.id}" } + v.unlocks.map { "uu:${it.achievementId}:${it.date}" }
        }.orEmpty()
        is Screen.ConsoleGames -> filteredConsoleGames().map { "cg:${it.id}" }
        is Screen.Images, Screen.WebSignIn, Screen.Login -> emptyList()
    }

    /** Focus the row you last used on this screen, or its first row. */
    /** Focus the row last used here, else the first row (skipping the top button when [preferContent]). */
    private fun restoreFocusHere(preferContent: Boolean = false) {
        val keys = keysHere()
        val last = lastFocus[focusScope()]
        focusTarget = when {
            last != null && last in keys -> last
            preferContent -> keys.firstOrNull { it != "want" } ?: keys.firstOrNull()
            else -> keys.firstOrNull()
        }
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
            HomeTab.Search -> catalog
            HomeTab.Profile -> profile
            HomeTab.Settings -> {
                if (update == UpdateState.Idle || update is UpdateState.Failed) checkForUpdates(manual = true)
                restoreFocus(t)
                return
            }
        }
        if (state == null || state is Load.Err) loadTab(t) else restoreFocus(t)
    }

    private fun loadTab(t: HomeTab) = when (t) {
        HomeTab.Playing -> launchLoad("recent", { recent = it }, { reconcileHidden(); restoreFocus(t) }) { recentGames() }
        HomeTab.ForYou -> launchLoad("suggested", { suggested = it }, { restoreFocus(t) }) { suggestions() }
        HomeTab.WantToPlay -> launchLoad("want", { wantToPlay = it }, { restoreFocus(t) }) { wantToPlay() }
        HomeTab.Library -> launchLoad("library", { library = it }, { restoreFocus(t) }) { library() }
        HomeTab.Unlocks -> launchLoad("unlocks", { unlocks = it }, { restoreFocus(t) }) { recentUnlocks() }
        HomeTab.Following -> launchLoad("following", { following = it }, { restoreFocus(t) }) { following() }
        HomeTab.Browse -> launchLoad("consoles", { consoles = it }, { restoreFocus(t) }) { consoles() }
        HomeTab.Search -> loadCatalog(force = catalog is Load.Ok)
        HomeTab.Profile -> loadProfile()
        HomeTab.Settings -> checkForUpdates(manual = true)
    }

    private fun loadProfile() = launchLoad("profile", { profile = it }) { profile() }

    private fun loadGame(s: Screen.Game) = launchLoad("game:${s.key}", { gameState(s).detail = it }, { if (screen == s) restoreFocusHere() }) {
        if (s.user != null) game(s.id, s.user) else game(s.id)
    }

    private fun loadCatalog(force: Boolean) = launchLoad("catalog", { catalog = it }, { catalogProgress = null; restoreFocus(HomeTab.Search) }) {
        catalogStore.load(this, force) { done, total -> catalogProgress = done to total }
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
