package org.qosp.notes.ui.appearance

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.databinding.FragmentAppearanceBinding
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.ColorScheme
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.preferences.DarkThemeMode
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.BaseFragment
import org.qosp.notes.ui.settings.SettingsViewModel
import org.qosp.notes.ui.theme.QuillpadTheme
import org.qosp.notes.ui.utils.viewBinding

/** Settings > Appearance (Black Pearl): theme gallery, "My theme" editor, mode, shapes and fonts. */
class AppearanceFragment : BaseFragment(R.layout.fragment_appearance) {
    private val binding by viewBinding(FragmentAppearanceBinding::bind)
    private val model: SettingsViewModel by viewModel()
    private val preferenceRepository: PreferenceRepository by inject()

    override val hasMenu = false
    override val toolbar: Toolbar get() = binding.layoutAppBar.toolbar
    override val toolbarTitle: String get() = getString(R.string.appearance_title)

    private var editingCustom by mutableStateOf(false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backFromEditor = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = setEditing(false)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backFromEditor)

        binding.composeView.setContent {
            QuillpadTheme {
              // Surface sets the content (text) color for the whole screen
              Surface(color = MaterialTheme.colorScheme.background) {
                val prefs by model.appPreferences.collectAsState(initial = AppPreferences())
                val custom by preferenceRepository.getCustomTheme().collectAsState(initial = CustomThemeColors.DEFAULT)
                backFromEditor.isEnabled = editingCustom
                if (editingCustom) {
                    ThemeEditorScreen(custom) { colors -> applyCustom(colors) }
                } else {
                    AppearanceScreen(
                        prefs = prefs,
                        custom = custom,
                        onThemeMode = { mode -> save(mode) { AppCompatDelegate.setDefaultNightMode(mode.mode) } },
                        onAmoled = { on -> save(if (on) DarkThemeMode.BLACK else DarkThemeMode.STANDARD, recreate = true) },
                        onScheme = { scheme -> save(scheme, recreate = true) },
                        onOpenCustom = { setEditing(true) },
                        onShape = { save(it, recreate = true) },
                        onFont = { save(it, recreate = true) },
                        onFab = { save(it) },
                    )
                }
              }
            }
        }
    }

    private fun setEditing(on: Boolean) {
        editingCustom = on
        toolbar.title = getString(if (on) R.string.preferences_color_scheme_custom else R.string.appearance_title)
    }

    private fun <T> save(pref: T, recreate: Boolean = false, after: () -> Unit = {})
        where T : Enum<T>, T : me.msoul.datastore.EnumPreference {
        lifecycleScope.launch {
            model.setPreferenceSuspending(pref)
            after()
            if (recreate) activity?.recreate()
        }
    }

    private fun applyCustom(colors: CustomThemeColors) {
        lifecycleScope.launch {
            preferenceRepository.setCustomTheme(colors)
            model.setPreferenceSuspending(ColorScheme.CUSTOM)
            setEditing(false)
            activity?.recreate()
        }
    }
}
