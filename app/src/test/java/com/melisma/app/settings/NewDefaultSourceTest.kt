package com.melisma.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local-only build: there is no default-on source added after installs
 * started keeping their own list, so nothing is ever offered to an older
 * install. The upstream behaviour this file used to pin — offering lrc.red
 * once to installs from before it existed — is gone with the online sources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewDefaultSourceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Cleared first: preferences outlive a test here, and what one test offered must not count for the next.
    private val prefs = context.getSharedPreferences("melisma", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test
    fun `a fresh install simply has the defaults`() {
        val enabled = SettingsStore(context).current.enabledProviders
        assertEquals(setOf("local-folder"), enabled)
        // Nothing is ever offered: the only default was already in the offered set by
        // definition, so offerNewDefaults() returns early and leaves the key unset.
        assertTrue(prefs.getStringSet(SettingsStore.KEY_PROVIDERS_OFFERED, null) == null)
    }

    @Test
    fun `an older install keeps its own choices, nothing new is offered`() {
        // Written by a version with the upstream defaults.
        prefs.edit().putStringSet(SettingsStore.KEY_PROVIDERS_ON, setOf("local", "amll")).commit()

        val enabled = SettingsStore(context).current.enabledProviders
        assertEquals(setOf("local", "amll"), enabled)
    }

    @Test
    fun `a backup from before keeps its choice, and no source is added on restore`() {
        val store = SettingsStore(context)

        store.restore(mapOf(SettingsStore.KEY_PROVIDERS_ON to setOf("local", "amll", "lrclib")), emptyMap())
        assertEquals(setOf("local", "amll", "lrclib"), store.current.enabledProviders)
    }
}
