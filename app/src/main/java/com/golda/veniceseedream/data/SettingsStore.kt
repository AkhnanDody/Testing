package com.golda.veniceseedream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** API key disimpan lokal di HP (DataStore), tidak di-hardcode di kode. */
class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_API = stringPreferencesKey("venice_api_key")
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_API].orEmpty() }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[KEY_API] = key.trim() }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_API) }
    }
}
