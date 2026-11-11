package org.qosp.notes.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tfcporciuncula.flow.FlowSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import me.msoul.datastore.EnumPreference
import me.msoul.datastore.getEnum
import me.msoul.datastore.setEnum
import java.io.IOException

/** The three colors of "My theme". Defaults match tools/palette-gen (CUSTOM_DEFAULT). */
/** App lock: enabled when a PIN hash is stored. [timeoutMinutes] away from the app before it locks (0 = at once). */
data class AppLockSettings(
    val pinHash: String? = null,
    val pinLength: Int = 0,
    val biometric: Boolean = true,
    val timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES,
) {
    val enabled: Boolean get() = pinHash != null

    companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 1
        val TIMEOUTS = listOf(0, 1, 5, 15)
    }
}

data class CustomThemeColors(val paper: Int, val surface: Int, val accent: Int) {
    companion object {
        val DEFAULT = CustomThemeColors(0xFFF4ECDD.toInt(), 0xFFFFFBF3.toInt(), 0xFF8E2F25.toInt())
    }
}

class PreferenceRepository(
    val dataStore: DataStore<Preferences>,
    private val sharedPreferences: FlowSharedPreferences
) {
    fun getEncryptedString(key: String): Flow<String> {
        return sharedPreferences.getString(key, "").asFlow()
    }

    fun getAll(): Flow<AppPreferences> {
        return dataStore.data
            .catch {
                if (it is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw it
                }
            }
            .map { prefs ->
                AppPreferences(
                    layoutMode = prefs.getEnum(),
                    themeMode = prefs.getEnum(),
                    darkThemeMode = prefs.getEnum(),
                    colorScheme = prefs.getEnum(),
                    cardShape = prefs.getEnum(),
                    noteFont = prefs.getEnum(),
                    fabSize = prefs.getEnum(),
                    sortMethod = prefs.getEnum(),
                    sortTagsMethod = prefs.getEnum(),
                    sortNavdrawerNotebooksMethod = prefs.getEnum(),
                    backupStrategy = prefs.getEnum(),
                    noteDeletionTime = prefs.getEnum(),
                    dateFormat = prefs.getEnum(),
                    timeFormat = prefs.getEnum(),
                    openMediaIn = prefs.getEnum(),
                    showDate = prefs.getEnum(),
                    editorFontSize = prefs.getEnum(),
                    showFabChangeMode = prefs.getEnum(),
                    groupNotesWithoutNotebook = prefs.getEnum(),
                    moveCheckedItems = prefs.getEnum(),
                    defaultEditorMode = prefs.getEnum(),
                    cloudService = prefs.getEnum(),
                    syncMode = prefs.getEnum(),
                    backgroundSync = prefs.getEnum(),
                    newNotesSyncable = prefs.getEnum(),
                    trustSelfSignedCertificate = prefs.getEnum(),
                )
            }
    }

    inline fun <reified T> get(): Flow<T> where T : Enum<T>, T : EnumPreference = dataStore.getEnum()

    suspend fun putEncryptedStrings(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) ->
            sharedPreferences.getString(key).setAndCommit(value)
        }
    }

    suspend fun <T> set(preference: T) where T : Enum<T>, T : EnumPreference = dataStore.setEnum(preference)

    /** "My theme" colors (ARGB): background ("paper"), cards ("surface") and accent. Used by ColorScheme.CUSTOM. */
    fun getCustomTheme(): Flow<CustomThemeColors> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            CustomThemeColors(
                paper = it[CUSTOM_PAPER_COLOR] ?: CustomThemeColors.DEFAULT.paper,
                surface = it[CUSTOM_SURFACE_COLOR] ?: CustomThemeColors.DEFAULT.surface,
                accent = it[CUSTOM_SEED_COLOR] ?: CustomThemeColors.DEFAULT.accent,
            )
        }

    suspend fun setCustomTheme(colors: CustomThemeColors) {
        dataStore.edit {
            it[CUSTOM_PAPER_COLOR] = colors.paper
            it[CUSTOM_SURFACE_COLOR] = colors.surface
            it[CUSTOM_SEED_COLOR] = colors.accent
        }
    }

    /** App lock (Black Pearl): the PIN is only stored as a salted hash, see ui/lock/PinHasher. */
    fun getAppLock(): Flow<AppLockSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            AppLockSettings(
                pinHash = it[APP_LOCK_PIN],
                pinLength = it[APP_LOCK_PIN_LENGTH] ?: 0,
                biometric = it[APP_LOCK_BIOMETRIC] ?: true,
                timeoutMinutes = it[APP_LOCK_TIMEOUT] ?: AppLockSettings.DEFAULT_TIMEOUT_MINUTES,
            )
        }

    suspend fun getAppLockOnce(): AppLockSettings = getAppLock().first()

    suspend fun setAppLockPin(hash: String?, length: Int) {
        dataStore.edit {
            if (hash == null) it.remove(APP_LOCK_PIN) else it[APP_LOCK_PIN] = hash
            it[APP_LOCK_PIN_LENGTH] = length
        }
    }

    suspend fun setAppLockOptions(biometric: Boolean, timeoutMinutes: Int) {
        dataStore.edit {
            it[APP_LOCK_BIOMETRIC] = biometric
            it[APP_LOCK_TIMEOUT] = timeoutMinutes
        }
    }

    /** Black Pearl: the sample note shipped in the APK is added only once (see components/SampleNote). */
    suspend fun isSampleNoteAdded(): Boolean = dataStore.data.first()[SAMPLE_NOTE_ADDED] ?: false

    suspend fun setSampleNoteAdded() {
        dataStore.edit { it[SAMPLE_NOTE_ADDED] = true }
    }

    companion object {
        val SAMPLE_NOTE_ADDED = booleanPreferencesKey("sample_note_added")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val APP_LOCK_PIN_LENGTH = intPreferencesKey("app_lock_pin_length")
        val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        val APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_minutes")
        const val NEXTCLOUD_INSTANCE_URL = "NEXTCLOUD_INSTANCE_URL"
        const val NEXTCLOUD_USERNAME = "NEXTCLOUD_USERNAME"
        const val NEXTCLOUD_PASSWORD = "NEXTCLOUD_PASSWORD"
        const val STORAGE_LOCATION = "STORAGE_LOCATION"
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color") // accent (name kept from 0.2.0)
        val CUSTOM_PAPER_COLOR = intPreferencesKey("custom_paper_color")
        val CUSTOM_SURFACE_COLOR = intPreferencesKey("custom_surface_color")
    }
}
