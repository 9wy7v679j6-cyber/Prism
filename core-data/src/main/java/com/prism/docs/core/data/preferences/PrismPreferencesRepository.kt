package com.prism.docs.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prism.docs.core.data.model.GlassIntensity
import com.prism.docs.core.data.model.PrismPreferences
import com.prism.docs.core.data.model.SafFolderGrant
import com.prism.docs.core.data.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.prismDataStore: DataStore<Preferences> by
  preferencesDataStore(
    name = "prism_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
  )

@Singleton
class PrismPreferencesRepository
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  private val json = Json { ignoreUnknownKeys = true }

  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val GLASS_INTENSITY = stringPreferencesKey("glass_intensity")
    val GLASS_CLARITY = floatPreferencesKey("glass_clarity")
    val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val SAF_FOLDER_GRANTS = stringPreferencesKey("saf_folder_grants_json")
    val FULL_ACCESS_BANNER_DISMISSED = booleanPreferencesKey("full_access_banner_dismissed")
  }

  val preferencesFlow: Flow<PrismPreferences> =
    context.prismDataStore.data
      .catch { error ->
        if (error is IOException) {
          emit(emptyPreferences())
        } else {
          throw error
        }
      }
      .map { prefs ->
          val intensity =
            prefs[Keys.GLASS_INTENSITY]?.let {
              runCatching { GlassIntensity.valueOf(it) }.getOrNull()
            } ?: GlassIntensity.STANDARD
          val defaultClarity =
            when (intensity) {
              GlassIntensity.SUBTLE -> 0.58f
              GlassIntensity.STANDARD -> 0.35f
              GlassIntensity.RICH -> 0.14f
            }
          val rawClarity = prefs[Keys.GLASS_CLARITY] ?: defaultClarity
          val safeClarity = if (rawClarity.isFinite()) rawClarity.coerceIn(0f, 1f) else defaultClarity
          PrismPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode =
              prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            glassIntensity = intensity,
            glassClarity = safeClarity,
            reduceTransparency = prefs[Keys.REDUCE_TRANSPARENCY] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            safFolderGrants = decodeGrants(prefs[Keys.SAF_FOLDER_GRANTS]),
            fullAccessBannerDismissed = prefs[Keys.FULL_ACCESS_BANNER_DISMISSED] ?: false,
          )
      }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.prismDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.prismDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setUseDynamicColor(enabled: Boolean) {
    context.prismDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
  }

  suspend fun setGlassIntensity(intensity: GlassIntensity) {
    context.prismDataStore.edit { it[Keys.GLASS_INTENSITY] = intensity.name }
  }

  suspend fun setGlassClarity(clarity: Float) {
    val clamped = if (clarity.isFinite()) clarity.coerceIn(0f, 1f) else 0.35f
    context.prismDataStore.edit { it[Keys.GLASS_CLARITY] = clamped }
  }

  suspend fun setReduceTransparency(enabled: Boolean) {
    context.prismDataStore.edit { it[Keys.REDUCE_TRANSPARENCY] = enabled }
  }

  suspend fun setReduceMotion(enabled: Boolean) {
    context.prismDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
  }

  suspend fun setFullAccessBannerDismissed(dismissed: Boolean) {
    context.prismDataStore.edit { it[Keys.FULL_ACCESS_BANNER_DISMISSED] = dismissed }
  }

  suspend fun addSafFolderGrant(grant: SafFolderGrant) {
    context.prismDataStore.edit { prefs ->
      val current = decodeGrants(prefs[Keys.SAF_FOLDER_GRANTS])
      val updated = (current.filterNot { it.uriString == grant.uriString } + grant)
      prefs[Keys.SAF_FOLDER_GRANTS] = json.encodeToString(updated)
    }
  }

  suspend fun removeSafFolderGrant(uriString: String) {
    context.prismDataStore.edit { prefs ->
      val current = decodeGrants(prefs[Keys.SAF_FOLDER_GRANTS])
      val updated = current.filterNot { it.uriString == uriString }
      prefs[Keys.SAF_FOLDER_GRANTS] = json.encodeToString(updated)
    }
  }

  private fun decodeGrants(raw: String?): List<SafFolderGrant> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<SafFolderGrant>>(raw) }.getOrDefault(emptyList())
  }
}
