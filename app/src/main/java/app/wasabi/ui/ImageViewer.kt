package app.wasabi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * A game's images full screen. D-pad left/right (or tapping the left or
 * right half) switches images; B goes back to the game.
 */
@Composable
fun ImageViewer(vm: AppViewModel, screen: Screen.Images) {
    val images = screen.images
    var index by remember(screen) { mutableIntStateOf(screen.index) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    fun move(delta: Int) {
        index = (index + delta).mod(images.size)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(requester)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { move(-1); true }
                        Key.DirectionRight -> { move(1); true }
                        else -> false
                    }
                }
                .pointerInput(images) {
                    detectTapGestures { pos -> move(if (pos.x < size.width / 2) -1 else 1) }
                },
            contentAlignment = Alignment.Center,
        ) {
            val (url, label) = images[index]
            AsyncImage(
                model = url,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        Text(
            "${images[index].second} · ${index + 1} of ${images.size}",
            color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp),
        )
        HintBar("←→" to "Switch image", "B" to "Back")
    }
}
