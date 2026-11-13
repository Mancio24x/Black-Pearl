package org.qosp.notes.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

/** What is read at app start: the lock settings and the saved wrong-PIN counter. */
data class AppLockState(val settings: AppLockSettings, val failures: Int = 0, val blockedUntil: Long = 0L)

/** Stored-hash stand-in used when the real settings cannot be read: it matches no PIN. */
const val UNREADABLE_PIN_HASH = "unreadable"

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

    private fun Preferences.toAppLockSettings() = AppLockSettings(
        pinHash = this[APP_LOCK_PIN],
        pinLength = this[APP_LOCK_PIN_LENGTH] ?: 0,
        biometric = this[APP_LOCK_BIOMETRIC] ?: true,
        timeoutMinutes = this[APP_LOCK_TIMEOUT] ?: AppLockSettings.DEFAULT_TIMEOUT_MINUTES,
    )

    /** App lock (Black Pearl): the PIN is only stored as a salted hash, see ui/lock/PinHasher. */
    fun getAppLock(): Flow<AppLockSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toAppLockSettings() }

    /**
     * Read at app start. Unlike [getAppLock] it never turns a read error into "no lock": it retries once and then
     * returns settings with an unreadable PIN, so the app starts locked (fingerprint / phone screen lock still open it).
     */
    suspend fun loadAppLockAtStart(): AppLockState =
        readAppLockState() ?: readAppLockState() // try again once
            ?: AppLockState(AppLockSettings(pinHash = UNREADABLE_PIN_HASH, pinLength = 0))

    /** One read of the lock settings and the wrong-PIN counter; null when the store cannot be read. */
    suspend fun readAppLockState(): AppLockState? = try {
        val prefs = dataStore.data.first()
        AppLockState(
            prefs.toAppLockSettings(),
            prefs[APP_LOCK_FAILURES] ?: 0,
            prefs[APP_LOCK_BLOCKED_UNTIL] ?: 0L,
        )
    } catch (e: IOException) {
        null
    }

    /** Wrong-PIN counter and the end of the wait (wall clock millis), kept so that killing the app does not reset them. */
    suspend fun setAppLockAttempts(failures: Int, blockedUntil: Long) {
        dataStore.edit {
            it[APP_LOCK_FAILURES] = failures
            it[APP_LOCK_BLOCKED_UNTIL] = blockedUntil
        }
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

    /** The recipe notebook's id, left out of "All notes" like the kit; null before it exists. */
    suspend fun getSampleNotebookId(): Long? = dataStore.data.first()[SAMPLE_NOTEBOOK_ID]

    suspend fun setSampleNotebookId(id: Long) {
        dataStore.edit { it[SAMPLE_NOTEBOOK_ID] = id }
    }

    /** True once a note added by an older version was looked for (and moved) into the recipe notebook. */
    suspend fun isSampleNoteMoved(): Boolean = dataStore.data.first()[SAMPLE_NOTE_MOVED] ?: false

    suspend fun setSampleNoteMoved() {
        dataStore.edit { it[SAMPLE_NOTE_MOVED] = true }
    }

    /** Notebooks of the shipped samples (Bible Study Kit, recipes) that "All notes" leaves out. */
    fun getSampleNotebookIds(): Flow<List<Long>> = dataStore.data.catch { emit(emptyPreferences()) }
        .map { listOfNotNull(it[BIBLE_KIT_NOTEBOOK_ID], it[SAMPLE_NOTEBOOK_ID]) }

    /** Black Pearl: the Bible Study Kit is added only once (see components/BibleStudyKit). */
    suspend fun isBibleKitAdded(): Boolean = dataStore.data.first()[BIBLE_KIT_ADDED] ?: false

    suspend fun setBibleKitAdded() {
        dataStore.edit { it[BIBLE_KIT_ADDED] = true }
    }

    /** The kit's notebook, left out of "All notes"; null before the kit is added. */
    fun getBibleKitNotebookId(): Flow<Long?> = dataStore.data.catch { emit(emptyPreferences()) }.map { it[BIBLE_KIT_NOTEBOOK_ID] }

    suspend fun setBibleKitNotebookId(id: Long) {
        dataStore.edit { it[BIBLE_KIT_NOTEBOOK_ID] = id }
    }

    companion object {
        val BIBLE_KIT_ADDED = booleanPreferencesKey("bible_study_kit_added")
        val BIBLE_KIT_NOTEBOOK_ID = longPreferencesKey("bible_study_kit_notebook_id")
        val SAMPLE_NOTE_ADDED = booleanPreferencesKey("sample_note_added")
        val SAMPLE_NOTEBOOK_ID = longPreferencesKey("sample_notebook_id")
        val SAMPLE_NOTE_MOVED = booleanPreferencesKey("sample_note_moved")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val APP_LOCK_PIN_LENGTH = intPreferencesKey("app_lock_pin_length")
        val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        val APP_LOCK_TIMEOUT = intPreferencesKey("app_lock_timeout_minutes")
        val APP_LOCK_FAILURES = intPreferencesKey("app_lock_failures")
        val APP_LOCK_BLOCKED_UNTIL = longPreferencesKey("app_lock_blocked_until")
        const val NEXTCLOUD_INSTANCE_URL = "NEXTCLOUD_INSTANCE_URL"
        const val NEXTCLOUD_USERNAME = "NEXTCLOUD_USERNAME"
        const val NEXTCLOUD_PASSWORD = "NEXTCLOUD_PASSWORD"
        const val STORAGE_LOCATION = "STORAGE_LOCATION"
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color") // accent (name kept from 0.2.0)
        val CUSTOM_PAPER_COLOR = intPreferencesKey("custom_paper_color")
        val CUSTOM_SURFACE_COLOR = intPreferencesKey("custom_surface_color")
    }
}
