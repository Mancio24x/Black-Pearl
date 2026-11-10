package org.qosp.notes.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.qosp.notes.R

/**
 * Round number pad with dots, used to unlock, to choose a PIN and to confirm it.
 * With [pinLength] the PIN is sent as soon as it is complete; without it (choosing a PIN) the ✓ key sends 4–6 digits.
 */
@Composable
fun PinEntry(
    title: String,
    message: String,
    isError: Boolean,
    pinLength: Int?,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    fun submit() {
        val pin = entered
        entered = ""
        onSubmit(pin)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(pinLength ?: PinHasher.MAX_LENGTH) { index ->
                val filled = index < entered.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(
                        if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        fun type(digit: Char) {
            if (!enabled || entered.length >= (pinLength ?: PinHasher.MAX_LENGTH)) return
            entered += digit
            if (pinLength != null && entered.length == pinLength) submit()
        }
        val rows = listOf("123", "456", "789")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { digit -> Key(onClick = { type(digit) }) { Text(digit.toString(), style = MaterialTheme.typography.headlineMedium) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                when {
                    onBiometric != null -> Key(stringResource(R.string.lock_use_biometric), tonal = false, onClick = onBiometric) {
                        Icon(painterResource(R.drawable.ic_fingerprint), null)
                    }
                    pinLength == null -> Key(
                        stringResource(R.string.lock_confirm), tonal = false,
                        onClick = { if (PinHasher.isValid(entered)) submit() },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_box_checked), null,
                            tint = if (PinHasher.isValid(entered)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    else -> Spacer(Modifier.size(72.dp))
                }
                Key(onClick = { type('0') }) { Text("0", style = MaterialTheme.typography.headlineMedium) }
                Key(stringResource(R.string.lock_delete), tonal = false, onClick = { entered = entered.dropLast(1) }) {
                    Icon(painterResource(R.drawable.ic_backspace), null)
                }
            }
        }
    }
}

@Composable
private fun Key(description: String? = null, tonal: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (tonal) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) { content() }
}

/** The lock screen shown over the app: PIN pad, wrong-PIN messages, wait after too many attempts. */
@Composable
fun LockScreen(appLock: AppLock, onBiometric: (() -> Unit)?) {
    var wrongLeft by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableIntStateOf(appLock.blockedSeconds()) }
    LaunchedEffect(blocked > 0) {
        while (blocked > 0) {
            delay(1000)
            blocked = appLock.blockedSeconds()
        }
    }
    val message = when {
        blocked > 0 -> pluralStringResource(R.plurals.lock_wait, blocked, blocked)
        wrongLeft > 0 -> pluralStringResource(R.plurals.lock_wrong_pin, wrongLeft, wrongLeft)
        else -> stringResource(R.string.lock_enter_pin)
    }
    key(appLock.settings.pinLength) {
        PinEntry(
            title = stringResource(R.string.lock_title),
            message = message,
            isError = blocked > 0 || wrongLeft > 0,
            pinLength = appLock.settings.pinLength.takeIf { it in PinHasher.MIN_LENGTH..PinHasher.MAX_LENGTH },
            enabled = blocked == 0,
            onBiometric = onBiometric,
        ) { pin ->
            when (val result = appLock.tryPin(pin)) {
                AppLock.PinResult.Unlocked -> wrongLeft = 0
                is AppLock.PinResult.Wrong -> wrongLeft = result.attemptsLeft
                is AppLock.PinResult.Blocked -> blocked = result.seconds
            }
        }
    }
}
