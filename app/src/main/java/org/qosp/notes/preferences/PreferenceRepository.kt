package org.qosp.notes.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.tfcporciuncula.flow.FlowSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import me.msoul.datastore.EnumPreference
import me.msoul.datastore.getEnum
import me.msoul.datastore.setEnum
import java.io.IOException

/** The three colors of "My theme". Defaults match tools/palette-gen (CUSTOM_DEFAULT). */
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

    companion object {
        const val NEXTCLOUD_INSTANCE_URL = "NEXTCLOUD_INSTANCE_URL"
        const val NEXTCLOUD_USERNAME = "NEXTCLOUD_USERNAME"
        const val NEXTCLOUD_PASSWORD = "NEXTCLOUD_PASSWORD"
        const val STORAGE_LOCATION = "STORAGE_LOCATION"
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color") // accent (name kept from 0.2.0)
        val CUSTOM_PAPER_COLOR = intPreferencesKey("custom_paper_color")
        val CUSTOM_SURFACE_COLOR = intPreferencesKey("custom_surface_color")
    }
}
