package org.qosp.notes.ui.lock

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentAppearanceBinding
import org.qosp.notes.preferences.AppLockSettings
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.BaseActivity
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.common.SheetChoice
import org.qosp.notes.ui.common.SheetGroup
import org.qosp.notes.ui.common.SheetRow
import org.qosp.notes.ui.common.SheetSwitch
import org.qosp.notes.ui.theme.QuillpadTheme
import org.qosp.notes.ui.utils.viewBinding
import org.qosp.notes.ui.widget.WidgetUpdateHelper

/**
 * Settings > App lock (Black Pearl): turn the lock on with a 4–6 digit PIN (typed twice), change it, allow
 * fingerprint / face / the phone's screen lock, choose after how long the app locks. Same layout as Appearance.
 */
class AppLockFragment : BaseFragment(R.layout.fragment_appearance) {
    private val binding by viewBinding(FragmentAppearanceBinding::bind)
    private val preferences: PreferenceRepository by inject()
    private val appLock: AppLock by inject()

    override val hasMenu = false
    override val toolbar: Toolbar get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String get() = getString(R.string.lock_settings_title)

    /** PIN steps in progress: check the current PIN, choose a new one, type it again. */
    private sealed interface Step {
        data class Verify(val then: Step?) : Step // null = turn the lock off
        data object Choose : Step
        data class Confirm(val pin: String) : Step
    }

    private var step by mutableStateOf<Step?>(null)
    private var error by mutableStateOf(false)
    private var waitSeconds by mutableIntStateOf(0)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leaveStep = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = go(null)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, leaveStep)
        val canUseBiometric = BiometricManager.from(requireContext())
            .canAuthenticate(BaseActivity.LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

        binding.composeView.setContent {
            QuillpadTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    val settings by preferences.getAppLock().collectAsState(initial = appLock.settings)
                    leaveStep.isEnabled = step != null
                    when (val current = step) {
                        null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                            SheetGroup {
                                SheetSwitch(R.drawable.ic_lock, stringResource(R.string.lock_enable), settings.enabled) { on ->
                                    go(if (on) Step.Choose else Step.Verify(null))
                                }
                                if (settings.enabled) {
                                    SheetRow(R.drawable.ic_pencil, stringResource(R.string.lock_change_pin)) { go(Step.Verify(Step.Choose)) }
                                    if (canUseBiometric) {
                                        SheetSwitch(R.drawable.ic_fingerprint, stringResource(R.string.lock_biometric), settings.biometric) {
                                            saveOptions(settings.copy(biometric = it))
                                        }
                                    }
                                }
                            }
                            if (settings.enabled) {
                                val timeouts = AppLockSettings.TIMEOUTS
                                SheetChoice(
                                    stringResource(R.string.lock_after),
                                    timeouts.map { minutes ->
                                        (if (minutes == 0) stringResource(R.string.lock_after_now)
                                        else pluralStringResource(R.plurals.lock_after_minutes, minutes, minutes)) to null
                                    },
                                    timeouts.indexOf(settings.timeoutMinutes),
                                ) { saveOptions(settings.copy(timeoutMinutes = timeouts[it])) }
                            }
                            Text(
                                stringResource(if (canUseBiometric) R.string.lock_help_fallback else R.string.lock_help_no_fallback),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                        is Step.Verify -> PinEntry(
                            title = stringResource(R.string.lock_current_pin),
                            message = if (waitSeconds > 0) pluralStringResource(R.plurals.lock_wait, waitSeconds, waitSeconds)
                            else stringResource(if (error) R.string.lock_wrong_pin_short else R.string.lock_enter_pin),
                            isError = error,
                            pinLength = settings.pinLength.takeIf { it in PinHasher.MIN_LENGTH..PinHasher.MAX_LENGTH },
                        ) { pin ->
                            // same attempt limit as the lock screen: this is not a way to guess the PIN
                            when (val result = appLock.verifyPin(pin)) {
                                is AppLock.PinResult.Blocked -> { error = true; waitSeconds = result.seconds }
                                is AppLock.PinResult.Wrong -> { error = true; waitSeconds = 0 }
                                AppLock.PinResult.Unlocked -> if (current.then == null) savePin(null) else go(current.then)
                            }
                        }
                        Step.Choose -> PinEntry(
                            title = stringResource(R.string.lock_choose_pin),
                            message = stringResource(R.string.lock_choose_pin_hint),
                            isError = false,
                            pinLength = null,
                        ) { pin -> go(Step.Confirm(pin)) }
                        is Step.Confirm -> PinEntry(
                            title = stringResource(R.string.lock_confirm_pin),
                            message = stringResource(if (error) R.string.lock_pins_differ else R.string.lock_confirm_pin_hint),
                            isError = error,
                            pinLength = current.pin.length,
                        ) { pin -> if (pin == current.pin) savePin(pin) else error = true }
                    }
                }
            }
        }
    }

    private fun go(next: Step?) {
        error = false
        waitSeconds = 0
        step = next
    }

    private fun savePin(pin: String?) {
        lifecycleScope.launch {
            preferences.setAppLockPin(pin?.let(PinHasher::hash), pin?.length ?: 0)
            appLock.settings = preferences.getAppLockOnce()
            WidgetUpdateHelper.updateAllWidgets(requireContext()) // show or hide the notes in the widget
            go(null)
        }
    }

    private fun saveOptions(settings: AppLockSettings) {
        lifecycleScope.launch {
            preferences.setAppLockOptions(settings.biometric, settings.timeoutMinutes)
            appLock.settings = preferences.getAppLockOnce()
        }
    }
}
