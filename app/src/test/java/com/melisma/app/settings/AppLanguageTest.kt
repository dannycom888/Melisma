package com.melisma.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.melisma.app.R
import com.melisma.app.util.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The in-app menu language switch.
 *
 * Android 13 added a per-app language setting in the system; this phone runs
 * Android 12, so the app does it itself: the choice is stored in
 * SharedPreferences, [AppLocale.wrap] applies it to a context's
 * configuration in `attachBaseContext`, and picking one recreates the
 * activity. These tests pin the storage and the resolution — the actual
 * recreation is a one-liner in the settings UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLanguageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Cleared first: preferences outlive a test here, and one test's choice
    // must not count for the next.
    private val prefs = context.getSharedPreferences(SettingsStore.PREFS_FILE, Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun `a fresh install follows the system language`() {
        assertEquals(AppLanguage.SYSTEM, SettingsStore(context).current.appLanguage)
        assertNull(AppLocale.localeFor(AppLanguage.SYSTEM))
        // Robolectric's default locale is English, so the unwrapped strings
        // are the English ones.
        assertEquals("Settings", context.getString(R.string.settings_settings))
    }

    @Test
    fun `choosing Chinese persists and the wrapped context reads Chinese strings`() {
        val store = SettingsStore(context)
        store.setAppLanguage(AppLanguage.CHINESE)

        assertEquals(AppLanguage.CHINESE, SettingsStore(context).current.appLanguage)
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLocale.localeFor(AppLanguage.CHINESE))

        val wrapped = AppLocale.wrap(context)
        assertEquals("zh", wrapped.resources.configuration.locales[0].language)
        assertEquals("设置", wrapped.getString(R.string.settings_settings))
    }

    @Test
    fun `choosing English reads English strings`() {
        SettingsStore(context).setAppLanguage(AppLanguage.ENGLISH)

        val wrapped = AppLocale.wrap(context)
        assertEquals("en", wrapped.resources.configuration.locales[0].language)
        assertEquals("Settings", wrapped.getString(R.string.settings_settings))
    }

    @Test
    fun `choosing System again follows the system language`() {
        val store = SettingsStore(context)
        store.setAppLanguage(AppLanguage.CHINESE)
        store.setAppLanguage(AppLanguage.SYSTEM)

        assertEquals(AppLanguage.SYSTEM, SettingsStore(context).current.appLanguage)
        assertEquals("Settings", AppLocale.wrap(context).getString(R.string.settings_settings))
    }

    @Test
    fun `an unknown stored value falls back to the system language`() {
        prefs.edit().putString(SettingsStore.KEY_APP_LANGUAGE, "KLINGON").commit()

        assertEquals(AppLanguage.SYSTEM, SettingsStore(context).current.appLanguage)
        assertEquals("Settings", AppLocale.wrap(context).getString(R.string.settings_settings))
    }

    @Test
    fun `localeFor maps every choice`() {
        assertNull(AppLocale.localeFor(AppLanguage.SYSTEM))
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLocale.localeFor(AppLanguage.CHINESE))
        assertEquals(Locale.ENGLISH, AppLocale.localeFor(AppLanguage.ENGLISH))
    }
}
