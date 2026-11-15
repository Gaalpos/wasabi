package app.wasabi

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.wasabi.data.Updater
import app.wasabi.ui.AppViewModel
import app.wasabi.ui.WasabiTheme
import app.wasabi.ui.ConsoleScreen
import app.wasabi.ui.GameScreen
import app.wasabi.ui.HomeScreen
import app.wasabi.ui.LoginScreen
import app.wasabi.ui.Palette
import app.wasabi.ui.Screen
import app.wasabi.ui.SettingsScreen
import app.wasabi.ui.UpdateState
import app.wasabi.ui.UserScreen
import app.wasabi.ui.RaWebWorker
import app.wasabi.ui.WebSignInScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WasabiTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
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
            KeyEvent.KEYCODE_BUTTON_START -> vm.openSettings()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    // Open Android's installer when an update finishes downloading or the
    // user presses Install; retried after granting "install unknown apps".
    val context = LocalContext.current
    LaunchedEffect(vm.installRequest) {
        val ready = vm.update as? UpdateState.Ready ?: return@LaunchedEffect
        if (vm.installRequest > 0 && !Updater.install(context, ready.apk)) vm.installBlocked()
    }
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
            is Screen.User -> UserScreen(vm, s)
            is Screen.ConsoleGames -> ConsoleScreen(vm, s)
            Screen.Settings -> SettingsScreen(vm)
            Screen.WebSignIn -> WebSignInScreen(vm)
        }
        // Hidden browser for Want to Play changes (only while one is running).
        RaWebWorker(vm)
    }
}
