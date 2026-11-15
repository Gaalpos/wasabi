package app.wasabi

import android.os.Bundle
import android.view.KeyEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.wasabi.data.Background
import app.wasabi.data.Updater
import app.wasabi.ui.AppViewModel
import app.wasabi.ui.Controls
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
import app.wasabi.ui.RaPageWorker
import app.wasabi.ui.SignOutDialog
import app.wasabi.ui.WebSignInScreen
import app.wasabi.ui.ImageViewer
import app.wasabi.ui.GameListScreen
import app.wasabi.ui.AwardsScreen
import app.wasabi.ui.StatsScreen
import app.wasabi.ui.AchievementScreen
import app.wasabi.ui.CommunityScreen
import app.wasabi.ui.OfflineBanner

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WasabiTheme { App(vm) } }
        hideNavigationBar()
        Background.ensureChannel(this)
        Background.schedule(this)   // keeps the widget and notifications check running
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
        vm.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()   // e.g. after the keyboard, a dialog or the installer
    }

    /** Set while a B press is closing the keyboard, so its release does nothing. */
    private var closingKeyboard = false

    private fun keyboardVisible(): Boolean =
        ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
    }

    /**
     * Hides Android's navigation bar (back / home / recents) for more room;
     * B goes back. Swiping up from the bottom edge shows it briefly.
     */
    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    // Last handled button press, to drop the duplicate some handhelds send
    // when the built-in controller shows up as two input devices.
    private var lastKey = 0
    private var lastKeyTime = 0L

    /**
     * Controller buttons are handled here, before any view sees them, so a
     * focused row, text field or web page can't swallow them:
     * L1/R1 tabs, X Want to Play, Y the screen's option (sort, filter,
     * favorite, remove), Start Settings, and the refresh button (R2 unless
     * changed in Settings). Only the first press counts: held-button
     * repeats and near-instant duplicates are ignored, and the release is
     * swallowed too. A and B keep Android's default handling (open, back).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // B with the on-screen keyboard open just closes the keyboard (and keeps
        // what you typed) instead of going back. Its release is swallowed too.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && keyboardVisible()) {
                if (event.repeatCount == 0) hideKeyboard()
                closingKeyboard = true
                return true
            }
            if (event.action == KeyEvent.ACTION_UP && closingKeyboard) {
                closingKeyboard = false
                return true
            }
        }
        if (vm.screen == Screen.Login) return super.dispatchKeyEvent(event)
        val action: (() -> Unit)? = when (event.keyCode) {
            Controls.refresh.keyCode -> vm::refresh
            KeyEvent.KEYCODE_BUTTON_L1 -> { { vm.cycleTab(-1) } }
            KeyEvent.KEYCODE_BUTTON_R1 -> { { vm.cycleTab(1) } }
            KeyEvent.KEYCODE_BUTTON_X -> vm::wantToPlayShortcut
            KeyEvent.KEYCODE_BUTTON_Y -> vm::option
            KeyEvent.KEYCODE_BUTTON_START -> vm::openSettings
            else -> null
        }
        if (action == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val duplicate = event.keyCode == lastKey && event.eventTime - lastKeyTime < 150
            lastKey = event.keyCode
            lastKeyTime = event.eventTime
            if (!duplicate) action()
        }
        return true
    }
}

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    // On the first screen, B only exits on a second press within 2 seconds.
    val activity = LocalContext.current as? android.app.Activity
    var lastBack by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = vm.stack.size <= 1) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) {
            activity?.finish()
        } else {
            lastBack = now
            activity?.let { android.widget.Toast.makeText(it, "Press B again to exit", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
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
        Column(Modifier.fillMaxSize()) {
        OfflineBanner()
        Box(Modifier.weight(1f)) {
        when (val s = vm.screen) {
            Screen.Login -> LoginScreen(vm)
            Screen.Home -> HomeScreen(vm)
            is Screen.Game -> GameScreen(vm, s)
            is Screen.User -> UserScreen(vm, s)
            is Screen.ConsoleGames -> ConsoleScreen(vm, s)
            Screen.WebSignIn -> WebSignInScreen(vm)
            is Screen.Images -> ImageViewer(vm, s)
            is Screen.GameList -> GameListScreen(vm, s)
            Screen.Awards -> AwardsScreen(vm)
            Screen.Stats -> StatsScreen(vm)
            is Screen.AchievementDetail -> AchievementScreen(vm, s)
            is Screen.Community -> CommunityScreen(vm, s)
        }
        }
        }
        // Hidden browser for Want to Play changes (only while one is running).
        RaWebWorker(vm)
        RaPageWorker(vm)
        SignOutDialog(vm)
    }
}
