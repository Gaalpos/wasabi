package app.wasabi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wasabi.R

@Composable
fun LoginScreen(vm: AppViewModel) {
    var user by rememberSaveable { mutableStateOf(vm.savedUser) }
    var key by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        vm.login(user, key)
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val landscape = maxWidth > maxHeight && maxHeight < 560.dp
        val brandIcon = if (maxHeight < 360.dp) 56.dp else 80.dp
        if (landscape) {
            // 16:9 / 4:3 landscape: branding on the left, form on the right.
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { Branding(iconSize = brandIcon) }
                Column(
                    Modifier.weight(1f).widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { LoginForm(vm, user, { user = it }, key, { key = it }, submit) }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Branding(iconSize = 88.dp)
                    Spacer(Modifier.height(4.dp))
                    LoginForm(vm, user, { user = it }, key, { key = it }, submit)
                }
            }
        }
    }
}

@Composable
private fun Branding(iconSize: Dp) {
    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(iconSize))
    Text("Wasabi", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text(
        "Your RetroAchievements progress, on your handheld.",
        color = Palette.Dim,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LoginForm(
    vm: AppViewModel,
    user: String,
    onUser: (String) -> Unit,
    key: String,
    onKey: (String) -> Unit,
    submit: () -> Unit,
) {
    OutlinedTextField(
        value = user,
        onValueChange = onUser,
        label = { Text("Username") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = key,
        onValueChange = onKey,
        label = { Text("Web API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Find your key at retroachievements.org → Settings → Authentication → Web API Key.",
        color = Palette.Faint,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
    vm.loginError?.let {
        Text(it, color = Palette.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    Button(
        onClick = { submit() },
        enabled = !vm.loginBusy,
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        if (vm.loginBusy) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("Sign in", fontWeight = FontWeight.SemiBold)
        }
    }
}
