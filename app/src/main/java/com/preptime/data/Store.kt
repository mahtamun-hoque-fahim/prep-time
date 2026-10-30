package com.preptime.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.preptime.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Serializable
data class Settings(val swatch: Int = 0, val mode: ThemeMode = ThemeMode.LIGHT)

private val Context.dataStore by preferencesDataStore("prep")
private val TEMPLATES = stringPreferencesKey("templates")
private val SETTINGS = stringPreferencesKey("settings")
private val json = Json { ignoreUnknownKeys = true }

/** Templates and theme settings, each kept as one JSON value. */
class Store(context: Context) {
    private val ds = context.applicationContext.dataStore

    val templates: Flow<List<Template>> = ds.data.map(::readTemplates)
    val settings: Flow<Settings> = ds.data.map { p ->
        p[SETTINGS]?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
    }

    suspend fun save(t: Template) {
        ds.edit { p ->
            val list = readTemplates(p)
            val i = list.indexOfFirst { it.id == t.id }
            p[TEMPLATES] = json.encodeToString(if (i < 0) list + t else list.toMutableList().also { it[i] = t })
        }
    }

    suspend fun delete(id: String) {
        ds.edit { p -> p[TEMPLATES] = json.encodeToString(readTemplates(p).filter { it.id != id }) }
    }

    suspend fun saveSettings(s: Settings) {
        ds.edit { it[SETTINGS] = json.encodeToString(s) }
    }

    private fun readTemplates(p: Preferences): List<Template> =
        p[TEMPLATES]?.let { json.decodeFromString<List<Template>>(it) } ?: emptyList()
}

val Context.store get() = Store(this) // cheap: the DataStore behind it is a per-process singleton
