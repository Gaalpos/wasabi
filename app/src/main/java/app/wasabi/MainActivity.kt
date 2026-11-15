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
import app.wasabi.ui.UpdateState
import app.wasabi.ui.UserScreen
import app.wasabi.ui.RaWebWorker
import app.wasabi.ui.WebSignInScreen
import app.wasabi.ui.ImageViewer

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

    // Last handled shoulder press, to drop the duplicate some handhelds send
    // when the built-in controller shows up as two input devices.
    private var lastShoulderKey = 0
    private var lastShoulderTime = 0L

    /**
     * LB/RB (and LT/RT) switch tabs. They're handled here, before any view
     * sees them, so a focused text field or web page can't swallow them.
     * Only the first press counts: held-button repeats and near-instant
     * duplicates are ignored, and the release is swallowed too.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val delta = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> -1
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> 1
            else -> 0
        }
        if (delta == 0 || vm.screen == Screen.Login) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val duplicate = event.keyCode == lastShoulderKey && event.eventTime - lastShoulderTime < 150
            lastShoulderKey = event.keyCode
            lastShoulderTime = event.eventTime
            if (!duplicate) vm.cycleTab(delta)
        }
        return true
    }

    /**
     * Face buttons the UI doesn't consume itself. A (confirm) and B (back)
     * are handled by Android's default gamepad mapping.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.screen == Screen.Login) return super.onKeyDown(keyCode, event)
        val action: (() -> Unit)? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_Y -> vm::option
            KeyEvent.KEYCODE_BUTTON_X -> vm::refresh
            KeyEvent.KEYCODE_BUTTON_START -> vm::openSettings
            else -> null
        }
        if (action == null) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) action()   // holding the button doesn't repeat it
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
            Screen.WebSignIn -> WebSignInScreen(vm)
            is Screen.Images -> ImageViewer(vm, s)
        }
        // Hidden browser for Want to Play changes (only while one is running).
        RaWebWorker(vm)
    }
}
