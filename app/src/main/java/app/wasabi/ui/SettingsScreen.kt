package app.wasabi.ui

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

/** Accent color and which home tabs are shown. Open with Start or the Settings tab button. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill("‹ Back", onClick = { vm.back() })
            Text("Settings", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f)) {
            CardGrid(vm.settingsListState) {
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
                items(HomeTab.entries, key = { "tab:${it.name}" }) { t ->
                    val on = t in vm.enabledTabs
                    val last = on && vm.enabledTabs.size == 1
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
        HintBar("A" to "Change", "B" to "Back")
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
