package com.melisma.app.util

import android.content.Context
import android.content.res.Configuration
import com.melisma.app.settings.AppLanguage
import com.melisma.app.settings.SettingsStore
import java.util.Locale

/**
 * The app's own menu language, chosen in Settings.
 *
 * Android 13 added a per-app language setting in the system; this phone runs
 * Android 12, which has none, so the app does it itself.
 *
 * How it works, and why not AppCompat: `AppCompatDelegate.setApplicationLocales`
 * only takes effect through `AppCompatActivity`, and this app's activity is a
 * plain `ComponentActivity` on a Compose theme — adopting AppCompat would mean
 * changing the activity's base class and theme for one setting. The classic
 * alternative needs no dependency: wrap the base context in
 * `attachBaseContext` with a [Configuration] carrying the chosen locale, so
 * every resource the activity (or service) reads resolves in that language.
 * It is applied in [com.melisma.app.MelismaApp], [com.melisma.app.MainActivity]
 * and [com.melisma.app.car.MelismaCarAppService] — the Application wrap alone
 * is not enough, because the system hands each activity and service its own
 * base context with the device configuration rather than the Application's.
 *
 * Changing the choice takes effect by recreating the activity; the process
 * stays alive, so playback tracking is not interrupted. A car screen already
 * connected when the choice changes picks it up on its next connection.
 */
object AppLocale {

    /** The [Locale] a choice resolves to, or null for "follow the system". */
    fun localeFor(choice: AppLanguage): Locale? = when (choice) {
        AppLanguage.SYSTEM -> null
        AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }

    /**
     * Reads the stored choice straight from SharedPreferences.
     *
     * `attachBaseContext` runs before `onCreate`, so the container — and its
     * [SettingsStore] — do not exist yet at the moment this is needed.
     * An unknown stored value falls back to the system language.
     */
    fun readChoice(context: Context): AppLanguage {
        val name = context
            .getSharedPreferences(SettingsStore.PREFS_FILE, Context.MODE_PRIVATE)
            .getString(SettingsStore.KEY_APP_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return runCatching { AppLanguage.valueOf(name) }.getOrDefault(AppLanguage.SYSTEM)
    }

    /**
     * A context whose resources resolve in the chosen language.
     *
     * Returns the base context untouched for "follow the system".
     */
    fun wrap(base: Context): Context {
        val locale = localeFor(readChoice(base)) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
