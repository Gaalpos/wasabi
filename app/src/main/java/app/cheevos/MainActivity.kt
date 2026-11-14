package app.cheevos

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cheevos.ui.AppViewModel
import app.cheevos.ui.CheevosTheme
import app.cheevos.ui.GameScreen
import app.cheevos.ui.HomeScreen
import app.cheevos.ui.LoginScreen
import app.cheevos.ui.Palette
import app.cheevos.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CheevosTheme { App(vm) } }
    }

    /**
     * Shoulder and face buttons the UI doesn't consume itself. A (confirm)
     * and B (back) are handled by Android's default gamepad mapping.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.screen == Screen.Login) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> vm.cycleTab(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> vm.cycleTab(1)
            KeyEvent.KEYCODE_BUTTON_Y -> vm.option()
            KeyEvent.KEYCODE_BUTTON_X -> vm.refresh()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .systemBarsPadding(),
    ) {
        when (val s = vm.screen) {
            Screen.Login -> LoginScreen(vm)
            Screen.Home -> HomeScreen(vm)
            is Screen.Game -> GameScreen(vm, s)
        }
    }
}
