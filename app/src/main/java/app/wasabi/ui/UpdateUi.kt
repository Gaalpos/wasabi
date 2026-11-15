package app.wasabi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576f)

/** Slim strip on the home screen when a newer version is out. */
@Composable
fun UpdateBanner(vm: AppViewModel) {
    if (!vm.showUpdateBanner) return
    val u = vm.update
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (u) {
                is UpdateState.Available -> "Wasabi ${u.info.version} is available"
                is UpdateState.Downloading -> "Downloading ${u.info.version}… ${(u.progress * 100).toInt()}%"
                is UpdateState.Ready -> "Wasabi ${u.info.version} is ready to install"
                else -> ""
            },
            color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        when (u) {
            is UpdateState.Available -> Pill("Update", onClick = { vm.startUpdate() }, selected = true, compact = true)
            is UpdateState.Ready -> Pill("Install", onClick = { vm.startUpdate() }, selected = true, compact = true)
            else -> Unit
        }
        if (u !is UpdateState.Downloading) Pill("Later", onClick = { vm.dismissUpdate() }, compact = true)
    }
}

/** The Updates block in Settings: status, one action, and release notes. */
@Composable
fun UpdateSettingsRow(vm: AppViewModel) {
    val u = vm.update
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusRow(
            "upd", vm,
            onClick = {
                when (u) {
                    is UpdateState.Available, is UpdateState.Ready -> vm.startUpdate()
                    is UpdateState.Checking, is UpdateState.Downloading -> Unit
                    else -> vm.checkForUpdates(manual = true)
                }
            },
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (u) {
                        UpdateState.Idle -> "Check for updates"
                        UpdateState.Checking -> "Checking…"
                        UpdateState.UpToDate -> "You're up to date"
                        is UpdateState.Available -> "Wasabi ${u.info.version} is available"
                        is UpdateState.Downloading -> "Downloading Wasabi ${u.info.version}…"
                        is UpdateState.Ready -> "Wasabi ${u.info.version} is ready to install"
                        is UpdateState.Failed -> "Couldn't update"
                    },
                    color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (u) {
                        UpdateState.Idle, UpdateState.UpToDate -> "Installed: ${vm.installedVersion}. Press A to check again."
                        UpdateState.Checking -> "Installed: ${vm.installedVersion}"
                        is UpdateState.Available -> "${mb(u.info.sizeBytes)} · press A to download and install"
                        is UpdateState.Downloading -> "${(u.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Press A to open the installer"
                        is UpdateState.Failed -> "${u.message} Press A to try again."
                    },
                    color = if (u is UpdateState.Failed) Palette.Error else Palette.Dim, fontSize = 11.sp,
                )
                if (u is UpdateState.Downloading) {
                    ProgressLine(u.progress, hardcore = true, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        val notes = when (u) {
            is UpdateState.Available -> u.info.notes
            is UpdateState.Ready -> u.info.notes
            else -> ""
        }
        if (notes.isNotBlank()) {
            Text(
                notes.replace("**", ""),
                color = Palette.Dim, fontSize = 12.sp, maxLines = 10, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
