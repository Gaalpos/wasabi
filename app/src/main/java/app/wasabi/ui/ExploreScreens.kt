package app.wasabi.ui

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Build
import app.wasabi.data.Console
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.data.ConsoleGame
import app.wasabi.data.FollowedUser
import app.wasabi.data.FriendGame
import app.wasabi.data.Unlock
import app.wasabi.data.UserView

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp)) {
        Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Palette.Dim, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- For You

@Composable
fun ForYouTab(vm: AppViewModel) {
    when (val s = vm.suggested) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val data = s.data
            if (data.isEmpty) {
                EmptyView("No suggestions yet. Play a few games, follow some players or fill your Want to Play list.")
            } else CardGrid(vm.listState(HomeTab.ForYou)) {
                if (data.almostDone.isNotEmpty()) {
                    fullWidthItem("h:a") { SectionTitle("Close to mastering", "You're at least halfway through these") }
                    items(data.almostDone, key = { "a${it.id}" }) { g ->
                        GameRow(vm, "s:a:${g.id}", g.title, g.iconUrl, g, g.award, sub = "${g.console} · ${g.total - g.shown} to go") {
                            vm.openGame(g.id, g.title)
                        }
                    }
                }
                if (data.friendsPlaying.isNotEmpty()) {
                    fullWidthItem("h:f") { SectionTitle("Your friends are playing", "Games people you follow played recently") }
                    items(data.friendsPlaying, key = { "f${it.game.id}" }) { f -> FriendGameRow(vm, f) }
                }
                if (data.fromWantToPlay.isNotEmpty()) {
                    fullWidthItem("h:w") { SectionTitle("From your Want to Play", "Random picks; refresh with X for others") }
                    items(data.fromWantToPlay, key = { "w${it.id}" }) { g -> WantRow(vm, "s:w:${g.id}", g) }
                }
            }
        }
    }
}

@Composable
private fun FriendGameRow(vm: AppViewModel, f: FriendGame) {
    val g = f.game
    val who = when (f.friends.size) {
        1 -> f.friends[0]
        2 -> "${f.friends[0]} and ${f.friends[1]}"
        else -> "${f.friends[0]} and ${f.friends.size - 1} others"
    }
    FocusRow("s:f:${g.id}", vm, onClick = { vm.openGame(g.id, g.title) }) {
        Thumb(g.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${g.console} · ${num(g.total)} achievements", color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Played by $who", color = Palette.Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        WantToggle(vm, g.id)
    }
}

// ---------------------------------------------------------------- Following

@Composable
fun FollowingTab(vm: AppViewModel) {
    when (val s = vm.following) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> if (s.data.isEmpty()) {
            EmptyView("You aren't following anyone yet. Follow players on retroachievements.org to see them here.")
        } else CardGrid(vm.listState(HomeTab.Following)) {
            fullWidthItem("count") {
                Text(
                    "Following ${num(s.data.size)}",
                    color = Palette.Dim, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                )
            }
            items(s.data, key = { it.user }) { u -> FollowedRow(vm, u) }
        }
    }
}

@Composable
private fun FollowedRow(vm: AppViewModel, u: FollowedUser) {
    FocusRow("f:${u.user}", vm, onClick = { vm.openUser(u.user) }) {
        Thumb(u.avatarUrl, 40.dp, shape = CircleShape)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    u.user, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (u.followsBack) Tag("Follows you", Palette.Soft)
            }
            Text(
                "${num(u.points)} pts" + if (u.softPoints > 0) " · ${num(u.softPoints)} soft" else "",
                color = Palette.Dim, fontSize = 11.sp,
            )
        }
    }
}

/** Another player's page: who they are, what they played, what they unlocked. */
@Composable
fun UserScreen(vm: AppViewModel, screen: Screen.User) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val sidePanel = maxWidth >= WideWidth && maxWidth > maxHeight
            when (val s = vm.user) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> if (sidePanel) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                        ) { UserHeader(vm, s.data) }
                        UserLists(vm, screen, s.data, withHeader = false, modifier = Modifier.weight(1f))
                    }
                } else {
                    UserLists(vm, screen, s.data, withHeader = true)
                }
            }
        }
        HintBar("A" to "Open", "X" to "Want to Play", "B" to "Back", REFRESH to "Refresh")
    }
}

@Composable
private fun UserHeader(vm: AppViewModel, v: UserView) {
    val p = v.profile
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumb(p.avatarUrl, 56.dp, shape = CircleShape)
            Column(Modifier.weight(1f)) {
                Text(p.user, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${num(p.points)} pts", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (p.softPoints > 0) Text("${num(p.softPoints)} softcore", color = Palette.Soft, fontSize = 11.sp)
            }
        }
        p.motto?.let { Text("“$it”", color = Palette.Dim, fontSize = 12.sp) }
        p.richPresence?.let { Text("Last seen: $it", color = Palette.Faint, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun UserLists(vm: AppViewModel, screen: Screen.User, v: UserView, withHeader: Boolean, modifier: Modifier = Modifier) {
    CardGrid(vm.userListState, modifier) {
        if (withHeader) fullWidthItem("header") { UserHeader(vm, v) }
        fullWidthItem("h:games") { SectionTitle("Recently played", "Open a game to see ${screen.name}'s progress") }
        if (v.recentGames.isEmpty()) {
            fullWidthItem("none:games") { Text("Nothing played recently.", color = Palette.Faint, fontSize = 12.sp) }
        }
        items(v.recentGames, key = { "g${it.id}" }) { g ->
            GameRow(
                vm, "ug:${g.id}", g.title, g.iconUrl, g, award = null,
                sub = if (g.lastPlayed != null) "${g.console} · ${ago(g.lastPlayed)}" else g.console,
            ) { vm.openGame(g.id, g.title, of = screen.name) }
        }
        fullWidthItem("h:unlocks") { SectionTitle("Recent unlocks", "Last 30 days") }
        if (v.unlocks.isEmpty()) {
            fullWidthItem("none:unlocks") { Text("No unlocks in the last 30 days.", color = Palette.Faint, fontSize = 12.sp) }
        }
        items(v.unlocks, key = { "u${it.achievementId}:${it.date}" }) { u ->
            UnlockRow(vm, "uu:${u.achievementId}:${u.date}", u) { vm.openGame(u.gameId, u.gameTitle, of = screen.name) }
        }
    }
}

@Composable
fun UnlockRow(vm: AppViewModel, key: String, u: Unlock, onClick: () -> Unit) {
    FocusRow(key, vm, onClick = onClick) {
        Thumb(u.badgeUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    u.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    "${u.points}",
                    color = if (u.hardcore) Palette.Accent else Palette.Soft,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(u.description, color = Palette.Dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${u.gameTitle} · ${ago(u.date)}" + if (u.hardcore) "" else " · softcore",
                color = Palette.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- Browse

/** Browse: a search box for every game, above Most played, Hacks and the systems. */
@Composable
fun BrowseTab(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        val searching = vm.searchQuery.isNotBlank()
        SearchField(
            value = vm.searchQuery,
            onValueChange = { vm.onSearchQueryChange(it) },
            placeholder = "Search all games",
            vm = vm,
            listState = if (searching) vm.searchListState else vm.listState(HomeTab.Browse),
            firstKey = { if (vm.searchQuery.isBlank()) "list:MostPlayed" else vm.searchResults().firstOrNull()?.let { "sg:${it.id}" } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            if (searching) SearchResults(vm) else BrowseList(vm)
        }
    }
}

@Composable
private fun BrowseList(vm: AppViewModel) {
    when (val s = vm.consoles) {
        null, Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
        is Load.Ok -> {
            val (favorites, others) = vm.sortedConsoles()
            CardGrid(vm.listState(HomeTab.Browse)) {
                items(GameListKind.entries, key = { "list:${it.name}" }) { kind ->
                    FocusRow("list:${kind.name}", vm, onClick = { vm.openGameList(kind) }) {
                        Icon(
                            if (kind == GameListKind.MostPlayed) Icons.Rounded.Star else Icons.Rounded.Build,
                            contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(kind.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(kind.subtitle, color = Palette.Dim, fontSize = 11.sp)
                        }
                    }
                }
                if (favorites.isNotEmpty()) {
                    fullWidthItem("h:fav") { SectionTitle("Favorites") }
                    items(favorites, key = { it.id }) { c -> ConsoleRow(vm, c) }
                    fullWidthItem("h:all") { SectionTitle("All systems") }
                } else {
                    fullWidthItem("hint") {
                        Text(
                            "Pick a system to search its games. Press Y or tap the star to keep favorites at the top.",
                            color = Palette.Dim, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
                items(others, key = { it.id }) { c -> ConsoleRow(vm, c) }
            }
        }
    }
}

@Composable
private fun ConsoleRow(vm: AppViewModel, c: Console) {
    val fav = vm.isFavoriteConsole(c.id)
    FocusRow("c:${c.id}", vm, onClick = { vm.openConsole(c) }) {
        Thumb(c.iconUrl, 32.dp)
        Text(
            c.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Star,
            contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
            tint = if (fav) Palette.Accent else Palette.Faint,
            modifier = Modifier.size(22.dp).clip(CircleShape).clickable { vm.toggleFavoriteConsole(c.id) },
        )
    }
}

/** Every game with achievements on one console, with a search box. */
@Composable
fun ConsoleScreen(vm: AppViewModel, screen: Screen.ConsoleGames) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(screen.name, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (vm.consoleGames as? Load.Ok)?.let {
                    val shown = vm.filteredConsoleGames().size
                    Text(
                        if (shown == it.data.size) "${num(shown)} games" else "${num(shown)} of ${num(it.data.size)} games",
                        color = Palette.Dim, fontSize = 11.sp,
                    )
                }
            }
            val fav = vm.isFavoriteConsole(screen.id)
            Pill(
                "Favorite",
                onClick = { vm.toggleFavoriteConsole(screen.id) },
                selected = fav,
                compact = true,
                icon = Icons.Rounded.Star,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                "Sort: ${vm.consoleSort.label}",
                onClick = { vm.cycleConsoleSort() },
                compact = true,
                icon = Icons.AutoMirrored.Rounded.List,
                focusKey = "csort", vm = vm,
            )
            if (vm.consoleSort == ConsoleSort.Players) {
                when (val p = vm.consolePlayers) {
                    null, Load.Loading -> Text("Loading player counts…", color = Palette.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
                    is Load.Err -> Text(p.message, color = Palette.Error, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
                    is Load.Ok -> Unit
                }
            }
        }
        SearchField(
            value = vm.consoleQuery,
            onValueChange = { vm.consoleQuery = it },
            placeholder = "Search games",
            vm = vm,
            listState = vm.consoleListState,
            firstKey = { vm.filteredConsoleGames().firstOrNull()?.let { "cg:${it.id}" } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Column(Modifier.weight(1f)) {
            when (val s = vm.consoleGames) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val games = vm.filteredConsoleGames()
                    if (games.isEmpty()) {
                        EmptyView(if (s.data.isEmpty()) "No games with achievements here yet." else "No games match \"${vm.consoleQuery.trim()}\".")
                    } else CardGrid(vm.consoleListState) {
                        items(games, key = { it.id }) { g -> ConsoleGameRow(vm, g) }
                    }
                }
            }
        }
        HintBar("A" to "Open", "X" to "Want to Play", "Y" to "Sort", "B" to "Back", REFRESH to "Refresh")
    }
}

@Composable
private fun ConsoleGameRow(vm: AppViewModel, g: ConsoleGame) {
    FocusRow("cg:${g.id}", vm, onClick = { vm.openGame(g.id, g.title) }) {
        Thumb(g.iconUrl, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(g.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val players = vm.consolePlayersOf(g.id)
            Text(
                "${num(g.achievements)} achievements · ${num(g.points)} pts" + if (players != null && players > 0) " · ${num(players)} players" else "",
                color = Palette.Dim, fontSize = 11.sp,
            )
        }
        WantToggle(vm, g.id)
    }
}

// ---------------------------------------------------------------- Search

/** Search results across every system (from the cached catalog). */
@Composable
private fun SearchResults(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            when (val s = vm.catalog) {
                null, Load.Loading -> {
                    val p = vm.catalogProgress
                    EmptyView(
                        if (p == null || p.second == 0) "Loading the game list…"
                        else "Downloading game lists: ${p.first} of ${p.second} consoles.\nThis only happens about once a week."
                    )
                }
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val results = vm.searchResults()
                    when {
                        vm.searchQuery.isBlank() -> EmptyView("Type a game name to search ${num(s.data.size)} games.")
                        results.isEmpty() -> EmptyView("No games match \"${vm.searchQuery.trim()}\".")
                        else -> CardGrid(vm.searchListState) {
                            val total = vm.searchMatchCount()
                            fullWidthItem("count") {
                                Text(
                                    if (total > results.size) "Showing ${num(results.size)} of ${num(total)}. Keep typing to narrow it down."
                                    else "${num(total)} games",
                                    color = Palette.Dim, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
                                )
                            }
                            items(results, key = { it.id }) { g ->
                                FocusRow("sg:${g.id}", vm, onClick = { vm.openGame(g.id, g.title) }) {
                                    Thumb(g.iconUrl, 44.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(g.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(g.console, color = Palette.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${num(g.achievements)} achievements · ${num(g.points)} pts", color = Palette.Faint, fontSize = 11.sp)
                                    }
                                    WantToggle(vm, g.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Website game lists

/** Most played games or hacks, from the website's game list, with a search box. */
@Composable
fun GameListScreen(vm: AppViewModel, screen: Screen.GameList) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(screen.kind.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val loaded = (vm.gameList as? Load.Ok)?.data
                Text(
                    if (loaded == null) screen.kind.subtitle
                    else vm.filteredGameList().size.let { shown -> if (shown == loaded.size) "${num(shown)} games" else "${num(shown)} of ${num(loaded.size)} games" },
                    color = Palette.Dim, fontSize = 11.sp,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                "Sort: ${vm.listSort.label}",
                onClick = { vm.cycleListSort() },
                compact = true,
                icon = Icons.AutoMirrored.Rounded.List,
                focusKey = "lsort", vm = vm,
            )
        }
        SearchField(
            value = vm.listQuery,
            onValueChange = { vm.listQuery = it },
            placeholder = "Search this list",
            vm = vm,
            listState = vm.gameListState,
            firstKey = { vm.filteredGameList().firstOrNull()?.let { "gl:${it.id}" } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Column(Modifier.weight(1f)) {
            when (val s = vm.gameList) {
                null, Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.message, vm) { vm.refresh() }
                is Load.Ok -> {
                    val games = vm.filteredGameList()
                    if (games.isEmpty()) {
                        EmptyView(if (s.data.isEmpty()) "Nothing here right now." else "No games match \"${vm.listQuery.trim()}\".")
                    } else CardGrid(vm.gameListState) {
                        items(games, key = { it.id }) { r -> RelatedGameRow(vm, "gl:${r.id}", r, current = false) }
                    }
                }
            }
        }
        HintBar("A" to "Open", "X" to "Want to Play", "Y" to "Sort", "B" to "Back", REFRESH to "Refresh")
    }
}

/**
 * A search box the D-pad can leave: down (or the keyboard's Search key)
 * closes the keyboard and selects the first game below. Left/right still
 * move the cursor; up from the first game comes back here.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    vm: AppViewModel,
    listState: LazyGridState,
    firstKey: () -> String?,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    fun leave() {
        keyboard?.hide()
        val key = firstKey()
        if (key == null) {
            focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
            return
        }
        scope.launch {
            listState.scrollToItem(0)   // so the first game is on screen to take focus
            vm.focusTarget = key
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { leave() }),
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                leave()
                true
            } else {
                false
            }
        },
    )
}
