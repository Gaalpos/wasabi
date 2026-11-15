package app.wasabi.ui

import app.wasabi.data.Background
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Settings tab: updates, website sign-in, accent color and which tabs show. */
@Composable
fun SettingsTab(vm: AppViewModel) {
    CardGrid(vm.listState(HomeTab.Settings)) {
        fullWidthItem("h:updates") { Heading("Updates", "New versions come from GitHub. Android asks you to confirm each install.") }
        fullWidthItem("upd") { UpdateSettingsRow(vm) }
        fullWidthItem("h:web") { Heading("RetroAchievements website", "Only used to add and remove Want to Play games") }
        fullWidthItem("web") {
            FocusRow("web", vm, onClick = { if (vm.webSignedIn) vm.askSignOut(SignOutKind.Website) else vm.openWebSignIn() }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.webSignedIn) "Signed in" else "Not signed in",
                        color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (vm.webSignedIn) "Press A to sign out of the website" else "Press A to sign in once",
                        color = Palette.Dim, fontSize = 11.sp,
                    )
                }
            }
        }
        if (vm.hiddenPlaying.isNotEmpty()) {
            fullWidthItem("h:hidden") { Heading("Playing", "Games you removed from the Playing tab") }
            fullWidthItem("hidden") {
                FocusRow("hidden", vm, onClick = { vm.showHiddenPlaying() }) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${vm.hiddenPlaying.size} hidden " + if (vm.hiddenPlaying.size == 1) "game" else "games",
                            color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text("Press A to show them in Playing again", color = Palette.Dim, fontSize = 11.sp)
                    }
                }
            }
        }
        fullWidthItem("h:notif") { Heading("Notifications", "Your new achievements, and games mastered or beaten by people you follow. Checked about every 15 minutes.") }
        fullWidthItem("notif") {
            val context = LocalContext.current
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                vm.setNotifications(granted)
            }
            FocusRow("notif", vm, onClick = {
                when {
                    vm.notificationsOn -> vm.setNotifications(false)
                    Build.VERSION.SDK_INT >= 33 && !Background.canNotify(context) -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> vm.setNotifications(true)
                }
            }) {
                Column(Modifier.weight(1f)) {
                    Text(if (vm.notificationsOn) "On" else "Off", color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Press A to turn ${if (vm.notificationsOn) "off" else "on"}", color = Palette.Dim, fontSize = 11.sp)
                }
                Switch(vm.notificationsOn, dimmed = false)
            }
        }
        fullWidthItem("h:controls") { Heading("Controls", "What each button does") }
        controlsHelp().forEachIndexed { i, (button, what) ->
            item(key = "ctl:$i") {
                FocusRow("ctl:$i", vm) {
                    Row(Modifier.width(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        button.split('/').forEach { ButtonBadge(if (it == REFRESH) Controls.refresh.label else it) }
                    }
                    Text(what, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        fullWidthItem("h:browselists") { Heading("Browse lists", "Choose which community lists appear in Browse") }
        items(CommunityKind.entries, key = { "bl:${it.name}" }) { kind ->
            val on = kind.name !in vm.hiddenBrowseLists
            FocusRow("bl:${kind.name}", vm, onClick = { vm.toggleBrowseList(kind) }) {
                Column(Modifier.weight(1f)) {
                    Text(kind.title, color = if (on) Palette.Text else Palette.Dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(kind.subtitle, color = Palette.Dim, fontSize = 11.sp)
                }
                Switch(on, dimmed = false)
            }
        }
        fullWidthItem("h:refresh") { Heading("Refresh button", "The button that reloads what you're looking at") }
        items(RefreshButton.entries, key = { "rb:${it.name}" }) { b ->
            val on = Controls.refresh == b
            FocusRow("rb:${b.name}", vm, onClick = { vm.setRefreshButton(b) }) {
                ButtonBadge(b.label)
                Text(b.label, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (on) Text("Selected", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        fullWidthItem("h:accent") { Heading("Accent color", "Used for highlights, selection and hardcore unlocks") }
        items(AccentColor.entries, key = { "ac:${it.name}" }) { c ->
            val on = Palette.accent == c
            FocusRow("ac:${c.name}", vm, onClick = { vm.setAccent(c) }) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(c.color)
                        .border(2.dp, if (on) Palette.Text else Color.Transparent, CircleShape),
                )
                Text(c.label, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (on) Text("Selected", color = c.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        fullWidthItem("h:tabs") { Heading("Tabs", "Choose which tabs appear on the home screen. At least one stays on.") }
        items(HomeTab.entries.filter { it != HomeTab.Settings }, key = { "tab:${it.name}" }) { t ->
            val on = t in vm.enabledTabs
            val last = on && vm.enabledTabs.count { it != HomeTab.Settings } == 1
            FocusRow("tab:${t.name}", vm, onClick = { vm.toggleTab(t) }) {
                Text(
                    t.label, color = if (on) Palette.Text else Palette.Dim, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Switch(on, dimmed = last)
            }
        }
    }
}

@Composable
private fun Heading(title: String, sub: String) {
    Column(Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp)) {
        Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Palette.Dim, fontSize = 11.sp)
    }
}

/** A read-only on/off switch drawn to match the theme (the whole row toggles it). */
@Composable
private fun Switch(on: Boolean, dimmed: Boolean) {
    val track = if (on) Palette.Accent.copy(alpha = if (dimmed) 0.5f else 1f) else Palette.Track
    Box(
        Modifier.size(width = 36.dp, height = 20.dp).clip(RoundedCornerShape(50)).background(track).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) Color.Black else Palette.Dim))
    }
}

/** Button → what it does, for the Controls section (keys as ButtonBadge names). */
fun controlsHelp(): List<Pair<String, String>> = listOf(
    "DPad" to "Move between items",
    "A" to "Open, select or change",
    "B" to "Go back (closes the keyboard first if it's open)",
    "X" to "Add the game to Want to Play, or remove it (on a game, or the selected game in a list)",
    "Y" to "Sort the library or a system's games, filter achievements, favorite a system, or remove a game from Playing",
    "L1/R1" to "Previous / next tab (on a game: its tabs)",
    REFRESH to "Refresh (pick the button below)",
    "Start" to "Settings",
)
