package com.melisma.app.ui

import com.melisma.app.lyrics.LyricsCache
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melisma.app.AppContainer
import com.melisma.app.settings.AppLanguage
import com.melisma.app.settings.BackgroundStyle
import com.melisma.app.settings.CanvasMode
import com.melisma.app.settings.ControlsPosition
import com.melisma.app.settings.FuriganaMode
import com.melisma.app.settings.LyricsFont
import com.melisma.app.settings.MediaPanelSide
import com.melisma.app.settings.PopupShape
import com.melisma.app.settings.Settings
import com.melisma.app.settings.SettingsBackup
import com.melisma.app.settings.SettingsStore
import com.melisma.app.settings.TextAnimationStyle
import com.melisma.app.settings.ViewMode
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.melisma.app.ui.components.AppIcons
import com.melisma.app.ui.components.ReorderableColumn
import com.melisma.app.settings.TranslationSource
import com.melisma.app.settings.CacheServerMode
import com.melisma.app.settings.ExtrasServerMode
import com.melisma.app.lyrics.LyricsRepository
import com.melisma.app.lyrics.HokkienDictionary
import com.melisma.app.lyrics.LyricsState
import com.melisma.app.lyrics.provider.CacheServerProvider
import com.melisma.app.lyrics.provider.LocalFolderLyricsProvider
import com.melisma.app.lyrics.romanize.HokkienDetector
import com.melisma.app.settings.ChineseReading
import com.melisma.app.settings.HokkienSpelling
import com.melisma.app.util.Script
import com.melisma.app.util.detectScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.melisma.app.R
import com.melisma.app.update.UpdateChecker
import com.melisma.app.update.Updater
import com.melisma.app.settings.ArtworkSource
import com.melisma.app.media.ServerSource
import com.melisma.app.lyrics.provider.SpotifyWebToken
import androidx.compose.ui.res.stringResource

/**
 * Whether the long-form explanations are showing.
 *
 * A composition local rather than a parameter on every row: the flag is read by a dozen
 * leaf composables and threading it through each call site would drown the settings list
 * it is meant to clarify.
 */
private val LocalSettingsHelp = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun Help(text: String) {
    if (!LocalSettingsHelp.current) return
    Text(
        text,
        color = Color.White.copy(alpha = 0.52f),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

private class ProviderInfo(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    /** Shown instead of the description when the provider is missing its credentials. */
    @param:StringRes val needsRes: Int? = null,
)

private val PROVIDER_INFO = mapOf(
    "local" to ProviderInfo(
        R.string.settings_your_own_files,
        R.string.settings_imported_lrc_ttml_always_tried_first_and_always,
    ),
    "local-folder" to ProviderInfo(
        R.string.settings_local_files,
        R.string.settings_your_own_lrc_files_in_a_folder_you_pick_the_only,
        needsRes = R.string.settings_pick_a_lyrics_folder_above,
    ),
    "cacheserver" to ProviderInfo(
        R.string.settings_cache_server,
        R.string.settings_your_own_server_holding_what_it_has_already_fetc,
        needsRes = R.string.settings_needs_a_url_under_developer,
    ),
    "applemusic" to ProviderInfo(
        R.string.settings_apple_music,
        R.string.settings_word_by_word_with_official_romanizations_and_tra,
        needsRes = R.string.settings_needs_a_developer_token_and_a_music_user_token_b,
    ),
    "lrcred" to ProviderInfo(
        R.string.settings_lrc_red,
        R.string.settings_apple_music_s_word_by_word_lyrics_romanizations,
    ),
    "amll" to ProviderInfo(
        R.string.settings_amll_ttml_database,
        R.string.settings_community_timed_word_by_word_public_domain_hand,
    ),
    "spotify" to ProviderInfo(
        R.string.settings_spotify_2,
        R.string.settings_the_lyrics_the_spotify_app_shows_matched_to_the,
        needsRes = R.string.settings_unavailable_spotify_closed_the_endpoint_this_nee,
    ),
    "netease" to ProviderInfo(
        R.string.settings_netease_cloud_music,
        R.string.settings_word_by_word_plus_hand_checked_romanization_and,
    ),
    "musixmatch" to ProviderInfo(
        R.string.settings_musixmatch_2,
        R.string.settings_word_by_word_for_most_western_music_no_account_n,
    ),
    "lrclib" to ProviderInfo(
        R.string.settings_lrclib_2,
        R.string.settings_open_community_database_no_account_needed,
    ),
)

/**
 * How long to keep watching with nothing playing.
 *
 * Ten minutes by default: long enough to survive the gap between two songs, a phone call or a look
 * at something else, and short enough that an app finished with in the morning is not still resident
 * at lunchtime.
 */
private val BACKGROUND_TIMEOUTS = listOf(
    2 to R.string.settings_2_min,
    10 to R.string.settings_10_min,
    30 to R.string.settings_30_min,
    0 to R.string.settings_never,
)

/** A short, practical list rather than every language ML Kit supports. */
private val TRANSLATION_TARGETS = listOf(
    "en" to R.string.settings_english, "es" to R.string.settings_spanish,
    "fr" to R.string.settings_french, "de" to R.string.settings_german,
    "it" to R.string.settings_italian, "pt" to R.string.settings_portuguese,
    "nl" to R.string.settings_dutch, "pl" to R.string.settings_polish,
    "ru" to R.string.settings_russian, "tr" to R.string.settings_turkish,
    "ar" to R.string.settings_arabic, "hi" to R.string.settings_hindi,
    "id" to R.string.settings_indonesian, "th" to R.string.settings_thai,
    "vi" to R.string.settings_vietnamese, "sv" to R.string.settings_swedish,
    "ja" to R.string.settings_japanese, "ko" to R.string.settings_korean,
    "zh" to R.string.settings_chinese,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    container: AppContainer,
    settings: Settings,
    accent: Color,
    songWriters: List<String>,
    onCopyAll: () -> Unit,
    onShowWelcome: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val store = container.settings
    val updater = container.updater
    val context = LocalContext.current
    var showCredits by remember { mutableStateOf(false) }

    // One section open at a time.
    //
    // There are a hundred-odd controls here, and a single flat list of them buries the
    // three anybody actually came for. Collapsed headings turn it back into something you
    // can read: a dozen names, and the one you want. Not remembered between openings —
    // coming back to a sheet folded exactly as you left it is worse than coming back to a
    // list, because the thing you were looking at last time is rarely the thing you want now.
    var openSection by remember { mutableStateOf<String?>(null) }
    // Off by default: the one-line description under each row is enough most of the time,
    // and the longer explanations get in the way once you know what things do.
    var showHelp by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14141A),
        contentColor = Color.White,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSettingsHelp provides showHelp) {
        val sheetScroll = rememberScrollState()
        // Where the scrolling area is on screen, so dragging a source towards the top or bottom
        // of the sheet can scroll it. Measured on the node outside the scroll modifier, which is
        // the viewport rather than the content, so this settles once and does not change again
        // as the sheet scrolls.
        var viewport by remember { mutableStateOf(Rect.Zero) }
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { viewport = it.boundsInWindow() }
                .verticalScroll(sheetScroll)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            if (showCredits) {
                CreditsPanel(accent = accent, onBack = { showCredits = false })
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_settings),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (showHelp) accent.copy(alpha = 0.3f)
                            else Color.White.copy(alpha = 0.08f),
                        )
                        .clickable { showHelp = !showHelp },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "?",
                        color = if (showHelp) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                if (showHelp) stringResource(R.string.settings_tap_again_to_hide_the_explanations)
                else stringResource(R.string.settings_tap_to_explain_what_each_setting_does),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            // The app's own menu language — kept out of any section so it is
            // visible the moment the sheet opens, and kept apart from the
            // "Language" section below, which is about the lyrics (romanization,
            // furigana, translation), not the menus. Picking one recreates the
            // activity at once, so the whole screen switches language without
            // restarting the app; the sheet itself closes with it.
            ChipGroup(
                label = stringResource(R.string.app_language_title),
                options = AppLanguage.entries.map { it to stringResource(it.labelRes) },
                selected = settings.appLanguage,
                accent = accent,
                onSelect = {
                    store.setAppLanguage(it)
                    (context as? Activity)?.recreate()
                },
            )
            Hint(stringResource(R.string.app_language_hint))

            Section(
                title = stringResource(R.string.settings_view),
                subtitle = stringResource(R.string.settings_cinema_where_the_controls_sit),
                open = openSection == "view",
                accent = accent,
                onToggle = { openSection = if (openSection == "view") null else "view" },
            ) {

                ChipGroup(
                    label = stringResource(R.string.settings_layout),
                    options = ViewMode.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.viewMode,
                    accent = accent,
                    onSelect = { store.setViewMode(it) },
                )
                Hint(
                    stringResource(R.string.settings_cinema_puts_the_album_art_beside_the_words_above),
                )

                if (settings.viewMode == ViewMode.CINEMA) {
                    ChipGroup(
                        label = stringResource(R.string.settings_artwork_on_the),
                        options = MediaPanelSide.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.mediaPanelSide,
                        accent = accent,
                        onSelect = { store.setMediaPanelSide(it) },
                    )
                }

                ChipGroup(
                    label = stringResource(R.string.settings_controls),
                    options = ControlsPosition.entries.map {
                        it to if (it == ControlsPosition.TOP) stringResource(R.string.settings_top) else stringResource(R.string.settings_bottom)
                    },
                    selected = settings.controlsPosition,
                    accent = accent,
                    onSelect = { store.setControlsPosition(it) },
                )

                ToggleRow(
                    title = stringResource(R.string.settings_volume_slider),
                    subtitle = stringResource(R.string.settings_a_volume_band_under_the_artwork_and_the_now_play),
                    checked = settings.showVolumeSlider,
                    accent = accent,
                    onCheckedChange = { store.setShowVolumeSlider(it) },
                )
                Help(
                    stringResource(R.string.settings_drives_the_phone_s_own_music_volume_not_the_play),
                )

                ChipGroup(
                    label = stringResource(R.string.settings_look_for_bigger_cover_art),
                    options = ArtworkSource.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.artworkSource,
                    accent = accent,
                    onSelect = { store.setArtworkSource(it) },
                )
                Help(
                    stringResource(R.string.settings_a_media_session_s_artwork_is_often_300_pixels_or),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_artist_image_and_full_size_cover),
                    subtitle = stringResource(R.string.settings_from_spotify_if_you_have_a_token_otherwise_apple),
                    checked = settings.useSpotifyExtras,
                    accent = accent,
                    onCheckedChange = { store.setUseSpotifyExtras(it) },
                )
                Help(
                    stringResource(R.string.settings_the_artist_s_photo_the_cover_at_full_size_rather),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_compact_mode),
                    subtitle = stringResource(R.string.settings_smaller_type_and_tighter_spacing_for_split_scree),
                    checked = settings.compactMode,
                    accent = accent,
                    onCheckedChange = { store.setCompactMode(it) },
                )
                Help(
                    stringResource(R.string.settings_meant_for_split_screen_or_a_small_window_the_flo),
                )
            }

            Section(
                title = stringResource(R.string.settings_popup_lyrics),
                subtitle = stringResource(R.string.settings_the_floating_window),
                open = openSection == "popup",
                accent = accent,
                onToggle = { openSection = if (openSection == "popup") null else "popup" },
            ) {

                ToggleRow(
                    title = stringResource(R.string.settings_popup_lyrics),
                    subtitle = stringResource(R.string.settings_carry_on_in_a_floating_window_over_other_apps),
                    checked = settings.popupLyricsEnabled,
                    accent = accent,
                    onCheckedChange = { store.setPopupLyricsEnabled(it) },
                )

                if (settings.popupLyricsEnabled) {
                    ToggleRow(
                        title = stringResource(R.string.settings_shrink_automatically),
                        subtitle = stringResource(R.string.settings_enter_the_window_when_you_leave_the_app_the_way),
                        checked = settings.popupAutoEnter,
                        accent = accent,
                        onCheckedChange = { store.setPopupAutoEnter(it) },
                    )
                Help(
                    stringResource(R.string.settings_on_android_12_and_up_the_system_does_the_shrinki),
                )
                    ChipGroup(
                        label = stringResource(R.string.settings_shape),
                        options = PopupShape.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.popupShape,
                        accent = accent,
                        onSelect = { store.setPopupShape(it) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_show_the_cover_in_the_window),
                        subtitle = stringResource(R.string.settings_only_in_cinema_view_off_leaves_the_whole_window),
                        checked = settings.popupShowArtwork,
                        accent = accent,
                        onCheckedChange = { store.setPopupShowArtwork(it) },
                    )
                    Help(
                        stringResource(R.string.settings_the_floating_window_follows_the_main_one_it_is_t),
                    )
                    Help(
                        stringResource(R.string.settings_the_window_s_background_is_held_still_rather_tha),
                    )
                }
            }

            Section(
                title = stringResource(R.string.settings_android_auto),
                subtitle = stringResource(R.string.settings_the_lyrics_on_a_car_screen),
                open = openSection == "car",
                accent = accent,
                onToggle = { openSection = if (openSection == "car") null else "car" },
            ) {
                Hint(
                    stringResource(R.string.settings_the_car_screen_follows_every_setting_on_this_pag),
                )
                Help(
                    stringResource(R.string.settings_melisma_is_not_on_google_play_so_android_auto_wi),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_plain_screen_in_the_car),
                    subtitle = stringResource(R.string.settings_two_lines_of_text_instead_of_the_moving_lyrics),
                    checked = settings.carSimpleScreen,
                    accent = accent,
                    onCheckedChange = { store.setCarSimpleScreen(it) },
                )
                Help(
                    stringResource(R.string.settings_off_a_car_gets_the_same_lyrics_this_phone_draws),
                )
            }

            Section(
                title = stringResource(R.string.settings_lyrics_display),
                subtitle = stringResource(R.string.settings_size_font_effects),
                open = openSection == "display",
                accent = accent,
                onToggle = { openSection = if (openSection == "display") null else "display" },
            ) {

                SliderRow(
                    label = stringResource(R.string.settings_text_size),
                    value = settings.fontScale,
                    range = 0.7f..1.6f,
                    display = "${(settings.fontScale * 100).toInt()}%",
                    accent = accent,
                    onChange = { store.setFontScale(it) },
                )

                ChipGroup(
                    label = stringResource(R.string.settings_font),
                    options = LyricsFont.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.font,
                    accent = accent,
                    onSelect = { store.setFont(it) },
                )

                ToggleRow(
                    title = stringResource(R.string.settings_depth_blur),
                    subtitle = stringResource(R.string.settings_lines_further_from_the_current_one_fall_out_of_f),
                    checked = settings.lineBlur,
                    accent = accent,
                    onCheckedChange = { store.setLineBlur(it) },
                )
                Help(
                    stringResource(R.string.settings_lines_away_from_the_current_one_are_drawn_as_a_b),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_simple_mode),
                    subtitle = stringResource(R.string.settings_flatter_contrast_and_no_letter_by_letter_emphasi),
                    checked = settings.simpleMode,
                    accent = accent,
                    onCheckedChange = { store.setSimpleMode(it) },
                )
                Help(
                    stringResource(R.string.settings_drops_the_letter_by_letter_emphasis_on_long_syll),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_minimal_mode),
                    subtitle = stringResource(R.string.settings_sung_lines_shrink_and_leave_the_page_instead_of),
                    checked = settings.minimalMode,
                    accent = accent,
                    onCheckedChange = { store.setMinimalMode(it) },
                )
                Help(
                    stringResource(R.string.settings_only_the_line_being_sung_and_the_ones_still_to_c),
                )

                ChipGroup(
                    label = stringResource(R.string.settings_text_animation),
                    options = TextAnimationStyle.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.textAnimationStyle,
                    accent = accent,
                    onSelect = { store.setTextAnimationStyle(it) },
                )
                Hint(stringResource(settings.textAnimationStyle.descriptionRes))
                Help(
                    stringResource(R.string.settings_calculate_reads_the_playhead_every_frame_so_it_i),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_highlight_the_line_you_hold),
                    subtitle = stringResource(R.string.settings_a_tinted_box_appears_under_your_finger_holding_a),
                    checked = settings.lineTapHighlight,
                    accent = accent,
                    onCheckedChange = { store.setLineTapHighlight(it) },
                )
                Help(
                    stringResource(R.string.settings_a_desktop_shows_this_when_the_mouse_is_over_a_li),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_duet_indent),
                    subtitle = stringResource(R.string.settings_inset_duet_lines_so_the_two_voices_read_as_separ),
                    checked = settings.duetLinePadding,
                    accent = accent,
                    onCheckedChange = { store.setDuetLinePadding(it) },
                )
                Help(
                    stringResource(R.string.settings_only_affects_songs_whose_lyrics_mark_two_voices),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_show_credits),
                    subtitle = stringResource(R.string.settings_songwriters_and_the_lyrics_source_after_the_last),
                    checked = settings.showCredits,
                    accent = accent,
                    onCheckedChange = { store.setShowCredits(it) },
                )
                Help(
                    stringResource(R.string.settings_printed_after_the_last_line_inside_the_scroll_ra),
                )
            }

            Section(
                title = stringResource(R.string.settings_background),
                subtitle = stringResource(R.string.settings_what_sits_behind_the_words),
                open = openSection == "background",
                accent = accent,
                onToggle = { openSection = if (openSection == "background") null else "background" },
            ) {

                ChipGroup(
                    label = stringResource(R.string.settings_style),
                    options = BackgroundStyle.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.backgroundStyle,
                    accent = accent,
                    onSelect = { store.setBackgroundStyle(it) },
                )
                if (settings.backgroundStyle == BackgroundStyle.ARTIST_HEADER &&
                    !container.spotifyExtrasAvailable
                ) {
                    Hint(
                        stringResource(R.string.settings_the_artist_background_needs_the_spotify_cookie_b),
                    )
                }

                if (settings.backgroundStyle == BackgroundStyle.COVER_ART ||
                    settings.backgroundStyle == BackgroundStyle.AUTO
                ) {
                    SliderRow(
                        label = stringResource(R.string.settings_cover_blur),
                        value = settings.backgroundBlur.toFloat(),
                        range = 0f..67f,
                        display = "${settings.backgroundBlur}px",
                        accent = accent,
                        onChange = { store.setBackgroundBlur(it.toInt()) },
                    )
                Help(
                    stringResource(R.string.settings_the_blur_comes_from_how_far_the_image_is_shrunk),
                )
                }

                ChipGroup(
                    label = stringResource(R.string.settings_spotify_canvas),
                    options = CanvasMode.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.canvasMode,
                    accent = accent,
                    onSelect = { store.setCanvasMode(it) },
                )
                if (settings.canvasMode != CanvasMode.OFF && !container.spotifyExtrasAvailable &&
                    !settings.cacheServerExtrasActive
                ) {
                    Hint(stringResource(R.string.settings_canvas_needs_the_same_spotify_token_as_spotify_l))
                }
                AnimatedVisibility(visible = settings.canvasMode != CanvasMode.OFF) {
                    Column(Modifier.fillMaxWidth()) {
                        ToggleRow(
                            title = stringResource(R.string.settings_show_its_still_on_data_saver),
                            subtitle = stringResource(R.string.settings_off_skips_canvas_over_mobile_data),
                            checked = settings.dataSaverStillCanvas,
                            accent = accent,
                            onCheckedChange = { store.setDataSaverStillCanvas(it) },
                        )
                        Help(
                            stringResource(R.string.settings_with_data_saver_on_over_mobile_data_a_still_of_t),
                        )
                    }
                }
                Help(
                    stringResource(R.string.settings_the_looping_video_spotify_shows_behind_some_trac),
                )
            }

            Section(
                title = stringResource(R.string.settings_timing),
                subtitle = stringResource(R.string.settings_offset_scrolling_taps),
                open = openSection == "timing",
                accent = accent,
                onToggle = { openSection = if (openSection == "timing") null else "timing" },
            ) {

                SliderRow(
                    label = stringResource(R.string.settings_sync_offset),
                    value = settings.syncOffsetMs.toFloat(),
                    range = -3000f..3000f,
                    display = "${settings.syncOffsetMs}ms",
                    accent = accent,
                    onChange = { store.setSyncOffset((it / 10).toInt() * 10) },
                    typed = TypedEntry(settings.syncOffsetMs, "ms", SettingsStore.SYNC_OFFSET_RANGE) {
                        store.setSyncOffset(it)
                    },
                )
                Hint(
                    when {
                        settings.syncOffsetMs == 0 -> stringResource(R.string.settings_in_step_with_the_player)
                        settings.syncOffsetMs > 0 -> stringResource(R.string.settings_lyrics_run_ms_early, settings.syncOffsetMs)
                        else -> stringResource(R.string.settings_lyrics_run_ms_late, -settings.syncOffsetMs)
                    } + stringResource(R.string.settings_bluetooth_needs_a_negative_value),
                )
                Help(
                    stringResource(R.string.settings_which_way_to_go_in_terms_of_what_you_notice_rath),
                )
                Row(Modifier.padding(bottom = 8.dp)) {
                    StepButton("−50", accent) { store.setSyncOffset(settings.syncOffsetMs - 50) }
                    Spacer(Modifier.width(8.dp))
                    StepButton(stringResource(R.string.settings_reset), accent) { store.setSyncOffset(0) }
                    Spacer(Modifier.width(8.dp))
                    StepButton("+50", accent) { store.setSyncOffset(settings.syncOffsetMs + 50) }
                }

                ToggleRow(
                    title = stringResource(R.string.settings_tap_a_line_to_jump_there),
                    subtitle = stringResource(R.string.settings_seeks_the_player_to_that_line),
                    checked = settings.tapLineToSeek,
                    accent = accent,
                    onCheckedChange = { store.setTapLineToSeek(it) },
                )
                Help(
                    stringResource(R.string.settings_only_works_on_lyrics_that_have_timings_holding_a),
                )

                SliderRow(
                    label = stringResource(R.string.settings_take_scrolling_back_after),
                    value = settings.autoScrollResumeMs / 1000f,
                    range = 0.5f..6f,
                    display = stringResource(R.string.settings_1fs).format(settings.autoScrollResumeMs / 1000f),
                    accent = accent,
                    onChange = { store.setAutoScrollResumeMs((it * 1000).toInt()) },
                )
                Help(
                    stringResource(R.string.settings_after_you_drag_the_lyrics_by_hand_this_is_how_lo),
                )
            }

            Section(
                title = stringResource(R.string.settings_players_to_follow),
                subtitle = stringResource(R.string.settings_which_apps_count_as_music),
                open = openSection == "players",
                accent = accent,
                onToggle = { openSection = if (openSection == "players") null else "players" },
            ) {

                Hint(
                    stringResource(R.string.settings_anything_that_plays_audio_publishes_the_same_kin),
                )
                Help(
                    stringResource(R.string.settings_entirely_in_the_sense_that_matters_for_the_batte),
                )

                val players = settings.seenPlayers.entries.sortedBy { it.value.lowercase() }
                if (players.isEmpty()) {
                    Hint(stringResource(R.string.settings_nothing_yet_play_something_and_the_app_that_play))
                } else {
                    for ((packageName, label) in players) {
                        ToggleRow(
                            title = label,
                            subtitle = packageName,
                            checked = packageName !in settings.ignoredPlayers,
                            accent = accent,
                            // Stored the other way round — an ignore list rather than an allow list —
                            // so a player nobody has an opinion about is followed. Anything else would
                            // mean a new music app silently doing nothing until it was found in here.
                            onCheckedChange = { store.setPlayerIgnored(packageName, !it) },
                        )
                    }
                    ActionRow(
                        title = stringResource(R.string.settings_forget_this_list),
                        subtitle = stringResource(R.string.settings_clears_the_names_above_they_come_back_as_each_ap),
                        accent = accent,
                        onClick = { store.forgetSeenPlayers() },
                    )
                }
            }

            Section(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_romanization_furigana_translation),
                open = openSection == "language",
                accent = accent,
                onToggle = { openSection = if (openSection == "language") null else "language" },
            ) {

                ToggleRow(
                    title = stringResource(R.string.settings_romanization),
                    subtitle = stringResource(R.string.settings_japanese_korean_chinese_cyrillic_and_greek_in_la),
                    checked = settings.showRomanization,
                    accent = accent,
                    onCheckedChange = { store.setShowRomanization(it) },
                )
                Help(
                    stringResource(R.string.settings_replaces_the_words_with_their_latin_transcriptio),
                )

                if (settings.showRomanization) {
                    ToggleRow(
                        title = stringResource(R.string.settings_drop_tone_marks),
                        subtitle = stringResource(R.string.settings_ni_hao_instead_of_n_h_o),
                        checked = settings.romanizationStripsDiacritics,
                        accent = accent,
                        onCheckedChange = { store.setStripDiacritics(it) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_keep_the_characters),
                        subtitle = stringResource(R.string.settings_shown_small_under_the_reading_above_any_translat),
                        checked = settings.showOriginalUnderRomanization,
                        accent = accent,
                        onCheckedChange = { store.setShowOriginalUnderRomanization(it) },
                    )
                    Help(
                        stringResource(R.string.settings_a_reading_cannot_always_be_trusted_on_its_own_ch),
                    )

                    ToggleRow(
                        title = stringResource(R.string.settings_recognise_taiwanese_hokkien),
                        subtitle = stringResource(R.string.settings_off_reads_every_chinese_song_as_mandarin),
                        checked = settings.detectHokkien,
                        accent = accent,
                        onCheckedChange = { store.setDetectHokkien(it) },
                    )
                    ChipGroup(
                        label = stringResource(R.string.settings_spell_hokkien_in),
                        options = HokkienSpelling.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.hokkienSpelling,
                        accent = accent,
                        onSelect = { store.setHokkienSpelling(it) },
                    )
                    Help(
                        stringResource(R.string.settings_hokkien_is_written_in_the_same_characters_as_man),
                    )
                    HokkienDictionaryRows(container.hokkienDictionary, accent)
                }

                ChipGroup(
                    label = stringResource(R.string.settings_furigana),
                    options = FuriganaMode.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.furigana,
                    accent = accent,
                    onSelect = { store.setFurigana(it) },
                )
                Hint(
                    if (settings.showRomanization) {
                        stringResource(R.string.settings_turn_romanization_off_to_see_it_furigana_is_a_gl)
                    } else {
                        stringResource(R.string.settings_prints_the_kana_reading_in_small_type_over_the_k)
                    },
                )

                ChipGroup(
                    label = stringResource(R.string.settings_translation),
                    options = TranslationSource.entries.map { it to stringResource(it.labelRes) },
                    selected = settings.translationSource,
                    accent = accent,
                    onSelect = { store.setTranslationSource(it) },
                )
                Hint(
                    when (settings.translationSource) {
                        TranslationSource.OFF -> stringResource(R.string.settings_no_translation_under_the_lyrics)
                        TranslationSource.PROVIDER ->
                            stringResource(R.string.settings_whatever_came_with_the_lyrics_free_instant_and_w)
                        TranslationSource.DEVICE ->
                            stringResource(R.string.settings_machine_translation_into_the_language_you_pick_b)
                    },
                )
                Help(
                    stringResource(R.string.settings_these_are_two_different_things_which_is_why_they),
                )

                if (settings.translationSource == TranslationSource.DEVICE) {
                    ChipGroup(
                        label = stringResource(R.string.settings_translate_into),
                        options = TRANSLATION_TARGETS.map { (code, labelRes) -> code to stringResource(labelRes) },
                        selected = settings.translationTarget,
                        accent = accent,
                        perRow = 4,
                        onSelect = { store.setTranslationTarget(it) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_download_models_on_wi_fi_only),
                        subtitle = stringResource(R.string.settings_each_language_is_roughly_30_mb),
                        checked = settings.translationWifiOnly,
                        accent = accent,
                        onCheckedChange = { store.setTranslationWifiOnly(it) },
                    )
                    Help(
                        stringResource(R.string.settings_only_affects_the_one_off_model_download_not_the),
                    )
                }
            }

            Section(
                title = stringResource(R.string.settings_where_lyrics_come_from),
                subtitle = stringResource(R.string.settings_your_own_lrc_files),
                open = openSection == "sources",
                accent = accent,
                onToggle = { openSection = if (openSection == "sources") null else "sources" },
            ) {
                // Local-only build: the folder is the only source. The picker
                // and the rescan live here, above the single provider row.
                //
                // The folder status is state, not a one-shot read: a rescan
                // that only ever changed a boolean left the user staring at
                // the same row with no idea whether anything had happened.
                // Scanning shows at once, the count shows when it lands
                // (zero included), and a folder that can no longer be opened
                // says so and offers the picker again instead of failing
                // silently.
                var folderStatus by remember {
                    mutableStateOf<LocalFolderLyricsProvider.IndexResult?>(null)
                }
                var folderScanning by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    folderStatus = container.localFolderLyrics.indexStatus()
                }
                val folderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    store.setLocalLyricsFolderUri(uri.toString())
                    scope.launch {
                        folderStatus = container.localFolderLyrics.refreshIndex()
                        container.lyrics.retry()
                    }
                }
                val folderName = settings.localLyricsFolderUri
                    ?.let {
                        runCatching {
                            DocumentFile.fromTreeUri(context, Uri.parse(it))?.name
                        }.getOrNull()
                    }
                val folderUnreadable = folderStatus is LocalFolderLyricsProvider.IndexResult.Unreadable
                ActionRow(
                    title = if (folderUnreadable) {
                        stringResource(R.string.settings_folder_unreadable)
                    } else {
                        stringResource(R.string.settings_local_lyrics_folder)
                    },
                    subtitle = if (folderUnreadable) {
                        stringResource(R.string.settings_tap_to_pick_folder_again)
                    } else {
                        folderName
                            ?: stringResource(R.string.settings_not_picked_yet_tap_to_choose_the_folder_with_you)
                    },
                    accent = accent,
                    onClick = { folderPicker.launch(null) },
                )
                if (folderUnreadable) {
                    Hint(
                        stringResource(R.string.settings_folder_unreadable_body),
                    )
                }
                ActionRow(
                    title = if (folderScanning) {
                        stringResource(R.string.settings_scanning)
                    } else if (folderStatus != null) {
                        stringResource(R.string.settings_lyrics_folder_rescanned)
                    } else {
                        stringResource(R.string.settings_rescan_local_lyrics)
                    },
                    subtitle = when (val status = folderStatus) {
                        null -> stringResource(R.string.settings_checking_folder)
                        is LocalFolderLyricsProvider.IndexResult.NoFolder ->
                            stringResource(R.string.settings_re_reads_the_folder_for_new_lrc_files)
                        is LocalFolderLyricsProvider.IndexResult.Ready ->
                            if (status.lrcCount == 0) {
                                stringResource(R.string.settings_no_lrc_files_found)
                            } else {
                                stringResource(R.string.settings_lrc_files_found, status.lrcCount)
                            }
                        is LocalFolderLyricsProvider.IndexResult.Unreadable ->
                            stringResource(R.string.settings_folder_unreadable_body)
                    },
                    accent = accent,
                    onClick = {
                        if (folderScanning) return@ActionRow
                        scope.launch {
                            folderScanning = true
                            folderStatus = container.localFolderLyrics.refreshIndex()
                            container.lyrics.retry()
                            folderScanning = false
                        }
                    },
                )
                Hint(
                    stringResource(R.string.settings_lyrics_come_only_from_lrc_files_in_this_folder_n),
                )

                // The cache server only appears once the developer options are on. Everyone else
                // would see a row for something they have never heard of and cannot use, and its
                // ranking would mean nothing.
                val listed = settings.providerOrder.filter {
                    it != "cacheserver" || settings.developerMode
                }
                ReorderableColumn(
                    items = listed,
                    keyOf = { it },
                    scroll = sheetScroll,
                    viewportInWindow = { viewport },
                    // The rows are a filtered view of the stored order, so the new order is
                    // written back over the slots that were visible and anything hidden — a cache
                    // server with developer options off — keeps the place it had.
                    onReordered = { order ->
                        store.setProviderOrder(settings.providerOrder.withVisibleOrder(order))
                    },
                ) { id, raised, handle ->
                    val info = PROVIDER_INFO[id]
                    val provider = container.providers.firstOrNull { it.id == id }
                    val configured = provider?.isConfigured ?: true
                    ProviderRow(
                        title = info?.let { stringResource(it.titleRes) } ?: id,
                        subtitle = info?.let {
                            stringResource(if (configured) it.descriptionRes else (it.needsRes ?: it.descriptionRes))
                        } ?: "",
                        warn = !configured,
                        enabled = id in settings.enabledProviders,
                        raised = raised,
                        handle = handle,
                        accent = accent,
                        onToggle = { store.setProviderEnabled(id, it) },
                    )
                }

                ToggleRow(
                    title = stringResource(R.string.settings_look_up_the_next_track_early),
                    subtitle = stringResource(R.string.settings_only_when_the_player_says_what_is_queued_next),
                    checked = settings.prefetchNextTrack,
                    accent = accent,
                    onCheckedChange = { store.setPrefetchNextTrack(it) },
                )
                Help(
                    stringResource(R.string.settings_fetches_the_queued_track_s_lyrics_while_the_curr),
                )
            }

            Section(
                title = stringResource(R.string.settings_tokens_and_endpoints),
                subtitle = stringResource(R.string.settings_all_optional_all_stay_on_this_device),
                open = openSection == "tokens",
                accent = accent,
                onToggle = { openSection = if (openSection == "tokens") null else "tokens" },
            ) {
                Hint(
                    stringResource(R.string.settings_all_optional_every_field_here_stays_on_this_devi),
                )
                Help(
                    stringResource(R.string.settings_these_are_your_own_credentials_read_from_a_brows),
                )

                SecretField(
                    label = stringResource(R.string.settings_spotify_sp_dc_cookie),
                    help = stringResource(R.string.settings_no_longer_enough_on_its_own_turning_the_cookie_i),
                    value = settings.spDcCookie.orEmpty(),
                    accent = accent,
                    onChange = { store.updateSpDcCookie(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_apple_music_developer_token),
                    help = stringResource(R.string.settings_a_jwt_valid_for_months_rather_than_an_hour_which),
                    value = settings.appleDeveloperToken.orEmpty(),
                    accent = accent,
                    onChange = { store.updateAppleDeveloperToken(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_apple_music_user_token),
                    help = stringResource(R.string.settings_the_media_user_token_cookie_from_a_signed_in_mus),
                    value = settings.appleMusicUserToken.orEmpty(),
                    accent = accent,
                    onChange = { store.updateAppleMusicUserToken(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_apple_music_storefront),
                    help = stringResource(R.string.settings_two_letter_country_code_for_the_catalogue_to_sea),
                    value = settings.appleStorefront,
                    accent = accent,
                    onChange = { store.updateAppleStorefront(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_musixmatch_user_token),
                    help = stringResource(R.string.settings_optional_the_app_mints_an_anonymous_token_for_it),
                    value = settings.musixmatchUserToken.orEmpty(),
                    accent = accent,
                    onChange = { store.updateMusixmatchUserToken(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_amll_ttml_instance),
                    help = stringResource(R.string.settings_the_community_lyrics_index_the_default_is_the_pr),
                    value = settings.amllBaseUrl,
                    accent = accent,
                    onChange = { store.updateAmllBaseUrl(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_lrclib_instance),
                    help = stringResource(R.string.settings_point_at_your_own_mirror_if_you_run_one),
                    value = settings.lrcLibBaseUrl,
                    accent = accent,
                    onChange = { store.updateLrcLibBaseUrl(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_netease_instance),
                    help = stringResource(R.string.settings_point_at_a_self_hosted_netease_api_if_the_public),
                    value = settings.neteaseBaseUrl,
                    accent = accent,
                    onChange = { store.updateNeteaseBaseUrl(it) },
                )

                SecretField(
                    label = stringResource(R.string.settings_netease_cookie),
                    help = stringResource(R.string.settings_optional_raises_the_per_ip_limits_and_unlocks_so),
                    value = settings.neteaseCookie.orEmpty(),
                    accent = accent,
                    onChange = { store.updateNeteaseCookie(it) },
                )
            }

            Section(
                title = stringResource(R.string.settings_this_track),
                subtitle = stringResource(R.string.settings_copy_import_look_it_up_again),
                open = openSection == "track",
                accent = accent,
                onToggle = { openSection = if (openSection == "track") null else "track" },
            ) {

                ActionRow(
                    title = stringResource(R.string.settings_copy_all_the_lyrics),
                    subtitle = stringResource(R.string.settings_puts_the_whole_song_on_the_clipboard),
                    accent = accent,
                    onClick = onCopyAll,
                )

                // Only offered when there is something to remove: importing the wrong file
                // otherwise leaves no way back, because a local file outranks every provider.
                var localRemoved by remember { mutableStateOf(false) }
                val hasLocal by produceState(initialValue = false, localRemoved) {
                    value = !localRemoved && container.lyrics.hasLocal()
                }
                if (hasLocal) {
                    ActionRow(
                        title = stringResource(R.string.settings_forget_the_imported_lyrics_file),
                        subtitle = stringResource(R.string.settings_goes_back_to_looking_this_track_up_online),
                        accent = accent,
                        onClick = {
                            scope.launch {
                                container.lyrics.removeLocal()
                                localRemoved = true
                            }
                        },
                    )
                }

                ActionRow(
                    title = stringResource(R.string.settings_look_this_track_up_again),
                    subtitle = stringResource(R.string.settings_drops_it_from_the_cache_and_asks_every_source_af),
                    accent = accent,
                    onClick = { container.lyrics.retry() },
                )

                if (!hasLocal) SourceRow(container = container, settings = settings, accent = accent)
                ReadAsRow(container = container, settings = settings, accent = accent)
            }

            Section(
                title = stringResource(R.string.settings_battery),
                subtitle = stringResource(R.string.settings_the_screen_standing_down_battery_saver),
                open = openSection == "battery",
                accent = accent,
                onToggle = { openSection = if (openSection == "battery") null else "battery" },
            ) {
                // Everything that trades a little of the app for charge lives here, wherever the
                // thing it affects is configured. Splitting them across the sections they belong to
                // — the screen under View, the window's background under Popup — meant nobody could
                // see what the app was costing them, or find the three switches that change it.
                Hint(stringResource(R.string.settings_every_setting_that_trades_something_for_battery))

                ToggleRow(
                    title = stringResource(R.string.settings_keep_the_screen_on_while_playing),
                    subtitle = stringResource(R.string.settings_only_while_something_is_playing_it_times_out_nor),
                    checked = settings.keepScreenOn,
                    accent = accent,
                    onCheckedChange = { store.setKeepScreenOn(it) },
                )
                Help(
                    stringResource(R.string.settings_the_screen_is_the_most_expensive_thing_on_a_phon),
                )

                ToggleRow(
                    title = stringResource(R.string.settings_hold_the_background_still_in_the_floating_window),
                    subtitle = stringResource(R.string.settings_living_stops_on_a_frame_instead_of_moving),
                    checked = settings.popupStillBackground,
                    accent = accent,
                    onCheckedChange = { store.setPopupStillBackground(it) },
                )
                Help(
                    stringResource(R.string.settings_the_window_is_what_stays_on_screen_over_whatever),
                )
                Help(
                    stringResource(R.string.settings_two_things_need_no_setting_because_they_cost_not),
                )

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Spacer(Modifier.height(10.dp))

                ChipGroup(
                    label = stringResource(R.string.settings_stop_watching_after),
                    options = BACKGROUND_TIMEOUTS.map { (minutes, labelRes) -> minutes to stringResource(labelRes) },
                    selected = settings.backgroundTimeoutMinutes,
                    accent = accent,
                    perRow = 4,
                    onSelect = { store.setBackgroundTimeoutMinutes(it) },
                )
                Hint(
                    when (val minutes = settings.backgroundTimeoutMinutes) {
                        0 -> stringResource(R.string.settings_never_stops_watches_for_a_track_until_you_turn_t)
                        else -> stringResource(R.string.settings_after_minutes_with_nothing_playing_and_the_app_o, minutes)
                    },
                )
                Help(
                    stringResource(R.string.settings_reading_what_is_playing_needs_notification_acces),
                )

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Spacer(Modifier.height(10.dp))

                val saving by container.saving.collectAsStateWithLifecycle()
                ToggleRow(
                    title = stringResource(R.string.settings_follow_the_system_battery_saver),
                    subtitle = if (saving.any) {
                        stringResource(R.string.settings_battery_saver_is_on_now_and_these_are_in_force)
                    } else {
                        stringResource(R.string.settings_ease_off_while_the_phone_is_in_battery_saver)
                    },
                    checked = settings.followBatterySaver,
                    accent = accent,
                    onCheckedChange = { store.setFollowBatterySaver(it) },
                )
                Help(
                    stringResource(R.string.settings_nothing_here_changes_anything_until_the_phone_pu),
                )

                // Each measure separately, because each one costs something different. Shown only
                // while they can do anything: three switches that are all no-ops explain nothing.
                AnimatedVisibility(visible = settings.followBatterySaver) {
                    Column(Modifier.fillMaxWidth()) {
                        ToggleRow(
                            title = stringResource(R.string.settings_hold_the_background_still),
                            subtitle = stringResource(R.string.settings_the_drifting_cover_stops_moving),
                            checked = settings.saverStillBackground,
                            accent = accent,
                            onCheckedChange = { store.setSaverStillBackground(it) },
                        )
                        Help(
                            stringResource(R.string.settings_the_biggest_saving_of_the_three_by_a_distance_an),
                        )
                        AnimatedVisibility(visible = settings.saverStillBackground) {
                            Column(Modifier.fillMaxWidth()) {
                                ToggleRow(
                                    title = stringResource(R.string.settings_freeze_living_on_a_frame),
                                    subtitle = stringResource(R.string.settings_off_shows_the_still_cover_art_instead),
                                    checked = settings.saverFreezeLiving,
                                    accent = accent,
                                    onCheckedChange = { store.setSaverFreezeLiving(it) },
                                )
                                Help(
                                    stringResource(R.string.settings_either_way_it_is_drawn_once_so_this_is_only_abou),
                                )
                                AnimatedVisibility(visible = settings.canvasMode != CanvasMode.OFF) {
                                    Column(Modifier.fillMaxWidth()) {
                                        ToggleRow(
                                            title = stringResource(R.string.settings_show_a_canvas_as_its_still),
                                            subtitle = stringResource(R.string.settings_off_shows_the_background_style_instead),
                                            checked = settings.saverStillCanvas,
                                            accent = accent,
                                            onCheckedChange = { store.setSaverStillCanvas(it) },
                                        )
                                        Help(
                                            stringResource(R.string.settings_a_still_is_drawn_once_like_the_rest_of_the_backg),
                                        )
                                    }
                                }
                            }
                        }
                        ToggleRow(
                            title = stringResource(R.string.settings_let_the_screen_time_out),
                            subtitle = stringResource(R.string.settings_even_while_the_music_is_playing),
                            checked = settings.saverReleaseScreen,
                            accent = accent,
                            onCheckedChange = { store.setSaverReleaseScreen(it) },
                        )
                        Help(
                            stringResource(R.string.settings_off_by_default_alone_among_these_the_screen_is_b),
                        )
                        ToggleRow(
                            title = stringResource(R.string.settings_skip_the_early_next_track_lookup),
                            subtitle = stringResource(R.string.settings_fetch_lyrics_when_the_track_starts_instead),
                            checked = settings.saverSkipPrefetch,
                            accent = accent,
                            onCheckedChange = { store.setSaverSkipPrefetch(it) },
                        )
                        Help(
                            stringResource(R.string.settings_costs_one_request_and_a_little_parsing_per_track),
                        )
                    }
                }
            }

            Section(
                title = stringResource(R.string.settings_storage_and_backup),
                subtitle = stringResource(R.string.settings_the_lyrics_cache_and_your_settings_in_a_file),
                open = openSection == "storage",
                accent = accent,
                onToggle = { openSection = if (openSection == "storage") null else "storage" },
            ) {

                var cacheCleared by remember { mutableStateOf(false) }
                val cacheSize by produceState(initialValue = -1L, cacheCleared) {
                    value = container.lyricsCacheSizeBytes()
                }
                ActionRow(
                    title = if (cacheCleared) stringResource(R.string.settings_cache_cleared) else stringResource(R.string.settings_clear_the_lyrics_cache),
                    subtitle = when {
                        cacheCleared -> stringResource(R.string.settings_forces_a_fresh_lookup_for_every_track)
                        cacheSize < 0 -> stringResource(R.string.settings_forces_a_fresh_lookup_for_every_track)
                        else -> stringResource(R.string.settings_1f_mb_stored_forces_a_fresh_lookup_for_every_tra)
                            .format(cacheSize / 1_048_576f)
                    },
                    accent = accent,
                    onClick = {
                        scope.launch {
                            container.lyrics.clearCache()
                            cacheCleared = true
                        }
                    },
                )

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Spacer(Modifier.height(10.dp))

                SettingsBackupRows(container = container, accent = accent)
            }

            Section(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version_updates_credits),
                open = openSection == "about",
                accent = accent,
                onToggle = { openSection = if (openSection == "about") null else "about" },
            ) {

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_melisma),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.settings_version_build, updater.currentVersion, updater.currentVersionCode, if (!updater.canUpdateInPlace) " · debug" else ""),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Text(
                    stringResource(R.string.settings_word_by_word_lyrics_for_whatever_your_phone_is_p),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                // ---- updates ------------------------------------------------
                ToggleRow(
                    title = stringResource(R.string.settings_check_for_updates_automatically),
                    subtitle = stringResource(R.string.settings_on_launch_at_most_every_few_hours),
                    checked = settings.autoUpdateCheck,
                    accent = accent,
                    onCheckedChange = { store.setAutoUpdateCheck(it) },
                )
                Help(
                    stringResource(R.string.settings_there_is_no_app_store_here_so_nothing_else_will),
                )

                val updateState by updater.state.collectAsStateWithLifecycle()
                ActionRow(
                    title = when (val state = updateState) {
                        is Updater.State.Checking -> stringResource(R.string.settings_checking)
                        is Updater.State.Available -> stringResource(R.string.settings_update_to, state.release.versionName)
                        is Updater.State.Downloading -> when {
                            state.fraction < 0f -> stringResource(R.string.settings_downloading_unknown)
                            else -> stringResource(R.string.settings_downloading, (state.fraction * 100).toInt())
                        }
                        is Updater.State.ReadyToInstall -> stringResource(R.string.settings_ready_to_install)
                        is Updater.State.UpToDate -> stringResource(R.string.settings_you_are_up_to_date)
                        is Updater.State.Failed -> stringResource(R.string.settings_check_for_updates)
                        Updater.State.Idle -> stringResource(R.string.settings_check_for_updates)
                    },
                    subtitle = when (val state = updateState) {
                        is Updater.State.Available ->
                            stringResource(R.string.settings_you_have_tap_to_download_and_install, updater.currentVersion)
                        is Updater.State.ReadyToInstall ->
                            stringResource(R.string.settings_downloaded_tap_to_install)
                        is Updater.State.Failed -> state.message
                        is Updater.State.UpToDate -> stringResource(R.string.settings_is_the_newest_release, updater.currentVersion)
                        else -> stringResource(R.string.settings_asks_github_for_the_newest_release)
                    },
                    accent = accent,
                    onClick = {
                        scope.launch {
                            when (val state = updateState) {
                                is Updater.State.Available ->
                                    updater.install(state.release)

                                // Already downloaded: hand it over again rather than starting a
                                // fresh check, which is the useful answer after Android's install
                                // screen was cancelled.
                                is Updater.State.ReadyToInstall ->
                                    updater.install(state.release)

                                is Updater.State.Downloading -> Unit
                                else -> updater.check(automatic = false)
                            }
                        }
                    },
                )

                if (!updater.canUpdateInPlace) {
                    Hint(
                        stringResource(R.string.settings_this_is_a_debug_build_which_installs_under_a_dif),
                    )
                }

                // Only for people who already run Shizuku; to everyone else this would be a
                // row about an app they do not have.
                if (updater.shizukuPresent) {
                    ToggleRow(
                        title = stringResource(R.string.settings_install_updates_through_shizuku),
                        subtitle = if (updater.shizukuGranted) {
                            stringResource(R.string.settings_permission_granted)
                        } else {
                            stringResource(R.string.settings_needs_permission_from_shizuku)
                        },
                        checked = settings.shizukuUpdates,
                        accent = accent,
                        onCheckedChange = { wanted ->
                            store.setShizukuUpdates(wanted)
                            if (wanted && !updater.shizukuGranted) updater.askShizuku()
                        },
                    )
                    Help(
                        stringResource(R.string.settings_an_update_still_needs_a_tap_without_this_play_pr),
                    )
                }

                (updateState as? Updater.State.Available)?.let { available ->
                    ActionRow(
                        title = stringResource(R.string.settings_skip, available.release.versionName),
                        subtitle = stringResource(R.string.settings_stops_asking_until_a_later_release_appears),
                        accent = accent,
                        onClick = { updater.skip(available.release) },
                    )
                }

                // ---- links --------------------------------------------------
                ActionRow(
                    title = stringResource(R.string.settings_source_code_and_releases),
                    subtitle = UpdateChecker.REPOSITORY,
                    accent = accent,
                    onClick = { openUrl(context, UpdateChecker.RELEASES_URL) },
                )

                ActionRow(
                    title = stringResource(R.string.settings_report_a_problem),
                    subtitle = stringResource(R.string.settings_issues_on_github),
                    accent = accent,
                    onClick = {
                        openUrl(context, "https://github.com/${UpdateChecker.REPOSITORY}/issues")
                    },
                )

                ActionRow(
                    title = stringResource(R.string.settings_read_the_welcome_guide_again),
                    subtitle = stringResource(R.string.settings_what_is_required_what_is_optional_and_what_each),
                    accent = accent,
                    onClick = onShowWelcome,
                )

                ActionRow(
                    title = stringResource(R.string.settings_credits_and_licences),
                    subtitle = stringResource(R.string.settings_built_on_spicy_lyrics_and_who_else_made_this_pos),
                    accent = accent,
                    onClick = { showCredits = true },
                )

                if (songWriters.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_this_track_was_written_by, songWriters.joinToString(", ")),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            Section(
                title = stringResource(R.string.settings_developer),
                subtitle = stringResource(R.string.settings_for_testing_a_server_of_your_own),
                open = openSection == "developer",
                accent = accent,
                onToggle = { openSection = if (openSection == "developer") null else "developer" },
            ) {

                ToggleRow(
                    title = stringResource(R.string.settings_developer_options),
                    subtitle = stringResource(R.string.settings_for_testing_a_lyrics_server_of_your_own),
                    checked = settings.developerMode,
                    accent = accent,
                    onCheckedChange = { store.setDeveloperMode(it) },
                )
                Help(
                    stringResource(R.string.settings_nothing_here_is_needed_to_use_the_app_and_with_t),
                )

                if (settings.developerMode) {
                    SecretField(
                        label = stringResource(R.string.settings_spotify_web_access_token),
                        help = stringResource(R.string.settings_brings_back_spotify_s_own_lyrics_the_artist_imag),
                        value = settings.spotifyWebToken.orEmpty(),
                        accent = accent,
                        onChange = { store.updateSpotifyWebToken(it) },
                    )

                    if (!settings.spotifyWebToken.isNullOrBlank()) {
                        val spotify = container.providers.firstOrNull { it.id == "spotify" }
                        Hint(
                            spotify?.unavailableReason
                                ?: stringResource(R.string.settings_accepted_spotify_s_lyrics_artist_image_cover_and),
                        )
                    }

                    ToggleRow(
                        title = stringResource(R.string.settings_renew_that_token_automatically),
                        subtitle = stringResource(R.string.settings_uses_your_sp_dc_cookie_in_a_hidden_webview_no_se),
                        checked = settings.spotifyBrowserToken,
                        accent = accent,
                        onCheckedChange = { store.setSpotifyBrowserToken(it) },
                    )
                    val harvest by SpotifyWebToken.harvest.collectAsStateWithLifecycle()
                    Hint(
                        when {
                            !settings.spotifyBrowserToken ->
                                stringResource(R.string.settings_off_so_the_token_above_stays_an_errand_about_one)
                            settings.spDcCookie.isNullOrBlank() ->
                                stringResource(R.string.settings_on_but_there_is_no_sp_dc_cookie_to_use_add_one_a)
                            harvest.running -> stringResource(R.string.settings_asking_the_player_for_a_token)
                            harvest.detail != null -> harvest.detail!!
                            else ->
                                stringResource(R.string.settings_on_renew_now_to_check_the_cookie_works_or_wait_f)
                        },
                    )

                    if (settings.spotifyBrowserToken && !settings.spDcCookie.isNullOrBlank()) {
                        val scope = rememberCoroutineScope()
                        ActionRow(
                            title = if (harvest.running) stringResource(R.string.settings_renewing) else stringResource(R.string.settings_renew_now),
                            subtitle = stringResource(R.string.settings_loads_the_player_and_reads_the_token_it_is_given),
                            accent = accent,
                            onClick = {
                                if (!harvest.running) {
                                    scope.launch { SpotifyWebToken.renewNow(container.settings) }
                                }
                            },
                        )

                        // Shown rather than hidden: the point of pressing the button is to find out
                        // whether it worked, and a masked field cannot tell you that.
                        harvest.token?.let { token ->
                            // Recomputed on a ticker rather than once: the sheet can sit open for
                            // longer than the token lives, and a number frozen at the moment it was
                            // drawn is the one thing a countdown must not be.
                            val expiresAt = harvest.expiresAt
                            val remaining by produceState(
                                expiresAt?.minus(System.currentTimeMillis()),
                                expiresAt,
                            ) {
                                while (expiresAt != null) {
                                    value = expiresAt - System.currentTimeMillis()
                                    delay(15_000)
                                }
                            }
                            val countdown = remaining?.let { left ->
                                when {
                                    left <= 0L -> stringResource(R.string.settings_expired_renew_for_another)
                                    left < 60_000L -> stringResource(R.string.settings_expires_in_under_a_minute)
                                    else -> stringResource(R.string.settings_expires_in_min, left / 60_000)
                                }
                            }
                            SecretField(
                                label = stringResource(R.string.settings_the_token_it_fetched),
                                help = stringResource(R.string.settings_read_only_and_kept_in_memory_rather_than_in_the, (countdown ?: "")),
                                value = token,
                                accent = accent,
                                onChange = {},
                                readOnly = true,
                            )
                        }
                    }
                    Help(
                        stringResource(R.string.settings_spotify_closed_the_endpoint_that_traded_a_cookie),
                    )

                    SecretField(
                        label = stringResource(R.string.settings_cache_server_url),
                        help = stringResource(R.string.settings_a_server_of_your_own_that_sits_in_front_of_the_f),
                        value = settings.cacheServerUrl.orEmpty(),
                        accent = accent,
                        onChange = { store.updateCacheServerUrl(it) },
                    )

                    SecretField(
                        label = stringResource(R.string.settings_cache_server_key),
                        help = stringResource(R.string.settings_only_needed_if_the_server_is_not_on_your_own_net),
                        value = settings.cacheServerKey.orEmpty(),
                        accent = accent,
                        onChange = { store.updateCacheServerKey(it) },
                    )

                    ChipGroup(
                        label = stringResource(R.string.settings_how_to_use_it),
                        options = CacheServerMode.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.cacheServerMode,
                        accent = accent,
                        onSelect = { store.setCacheServerMode(it) },
                    )
                    Hint(
                        when (settings.cacheServerMode) {
                            CacheServerMode.PARALLEL ->
                                stringResource(R.string.settings_asked_alongside_every_source_you_have_enabled_an)
                            CacheServerMode.ONLY ->
                                stringResource(R.string.settings_the_only_source_asked_whatever_its_ranking_says)
                        },
                    )
                    Help(
                        stringResource(R.string.settings_two_modes_because_they_answer_different_question),
                    )

                    ToggleRow(
                        title = stringResource(R.string.settings_ask_the_server_for_artwork_tempo_and_canvas),
                        subtitle = stringResource(R.string.settings_not_just_the_words),
                        checked = settings.cacheServerExtras,
                        accent = accent,
                        onCheckedChange = { store.setCacheServerExtras(it) },
                    )
                    Help(
                        stringResource(R.string.settings_the_reason_to_want_this_a_spotify_token_lasts_an),
                    )
                    AnimatedVisibility(visible = settings.cacheServerExtras) {
                        Column(Modifier.fillMaxWidth()) {
                            ChipGroup(
                                label = stringResource(R.string.settings_where_the_extras_come_from),
                                options = ExtrasServerMode.entries.map { it to stringResource(it.labelRes) },
                                selected = settings.cacheServerExtrasMode,
                                accent = accent,
                                onSelect = { store.setCacheServerExtrasMode(it) },
                            )
                            Hint(
                                when (settings.cacheServerExtrasMode) {
                                    ExtrasServerMode.FALLBACK ->
                                        stringResource(R.string.settings_asked_when_no_token_on_this_phone_can_answer_a_l)
                                    ExtrasServerMode.ONLY ->
                                        stringResource(R.string.settings_artwork_tempo_and_canvas_come_from_the_server_al)
                                },
                            )
                            Help(
                                stringResource(R.string.settings_separate_from_how_to_use_it_above_which_covers_t),
                            )
                        }
                    }

                    if (settings.cacheServerUrl.isNullOrBlank()) {
                        Hint(stringResource(R.string.settings_no_url_set_so_the_cache_server_is_not_being_aske))
                    }

                    // ---- what each source actually said --------------------------
                    var probe by remember { mutableStateOf<List<LyricsRepository.SourceReport>?>(null) }
                    var probing by remember { mutableStateOf(false) }

                    ActionRow(
                        title = if (probing) stringResource(R.string.settings_asking_every_source) else stringResource(R.string.settings_test_the_sources),
                        subtitle = stringResource(R.string.settings_asks_each_one_about_this_track_and_reports_what),
                        accent = accent,
                        onClick = {
                            if (!probing) {
                                scope.launch {
                                    probing = true
                                    probe = runCatching { container.lyrics.diagnose() }.getOrNull()
                                    probing = false
                                }
                            }
                        },
                    )
                    // Under its own button, so it cannot be mistaken for the server's.
                    probe?.forEach { report -> ProbeRow(report.name, report.outcome, working = false) }
                    Help(
                        stringResource(R.string.settings_a_source_that_is_switched_off_one_that_cannot_re),
                    )

                    // ---- and what the server says about its own ------------------
                    var serverProbe by remember { mutableStateOf<List<ServerSource>?>(null) }
                    var serverProbing by remember { mutableStateOf(false) }
                    var serverFailed by remember { mutableStateOf(false) }

                    ActionRow(
                        title = if (serverProbing) stringResource(R.string.settings_asking_the_server) else stringResource(R.string.settings_test_the_server),
                        subtitle = stringResource(R.string.settings_asks_your_server_which_of_its_own_sources_and_to),
                        accent = accent,
                        onClick = {
                            if (!serverProbing) {
                                scope.launch {
                                    serverProbing = true
                                    serverFailed = false
                                    val result = runCatching {
                                        container.cacheServerStatus()
                                    }.getOrNull()
                                    serverProbe = result
                                    serverFailed = result == null
                                    serverProbing = false
                                }
                            }
                        },
                    )
                    if (serverFailed) {
                        Hint(
                            stringResource(R.string.settings_no_answer_check_the_url_and_if_the_server_is_not),
                        )
                    }
                    serverProbe?.forEach { source ->
                        ProbeRow(
                            source.name,
                            buildString {
                                append(if (source.ok) "✓ " else "· ")
                                append(source.detail.ifBlank { if (source.ok) "working" else "no" })
                                source.ms?.let { append(" (").append(it).append("ms)") }
                            },
                            working = source.ok,
                        )
                    }
                    Help(
                        stringResource(R.string.settings_the_test_above_cannot_answer_this_one_pointing_t),
                    )

                    // ---- what each player tells Android --------------------------
                    var sessionReport by remember { mutableStateOf<String?>(null) }
                    val clipboard = LocalClipboard.current

                    ActionRow(
                        title = stringResource(R.string.settings_what_players_publish),
                        subtitle = stringResource(R.string.settings_everything_each_media_session_on_the_phone_says),
                        accent = accent,
                        onClick = {
                            scope.launch { sessionReport = container.media.describeSessions() }
                        },
                    )
                    sessionReport?.let { report ->
                        SelectionContainer {
                            Text(
                                report,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(10.dp),
                            )
                        }
                        val mediaSessionsLabel = stringResource(R.string.settings_media_sessions)
                        val copiedMessage = stringResource(R.string.settings_copied)
                        ActionRow(
                            title = stringResource(R.string.settings_copy_the_report),
                            subtitle = stringResource(R.string.settings_read_it_before_posting_it_anywhere_a_player_can),
                            accent = accent,
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(android.content.ClipData.newPlainText(mediaSessionsLabel, report)),
                                    )
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                    Help(
                        stringResource(R.string.settings_how_exactly_a_track_can_be_looked_up_depends_on),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        }
    }

    LaunchedEffect(Unit) { sheetState.expand() }
}

/**
 * Attribution, in the app rather than only in the repository.
 *
 * This app is a port of somebody else's work, under a licence that asks to be
 * acknowledged; the people who read that acknowledgement are the people using it.
 */
@Composable
private fun CreditsPanel(accent: Color, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Close,
                contentDescription = stringResource(R.string.settings_back_to_settings),
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.settings_credits_and_licences), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }

    Credit(
        title = stringResource(R.string.settings_spicy_lyrics_spikerko),
        body = stringResource(R.string.settings_this_app_is_a_port_of_spicy_lyrics_its_look_its),
        link = "github.com/Spikerko/spicy-lyrics",
        accent = accent,
    )
    Credit(
        title = "lrc.red",
        body = stringResource(R.string.settings_apple_music_s_lyrics_by_isrc_free_and_without_a),
        link = "lrc.red",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_spr_fraktality_mit),
        body = stringResource(R.string.settings_the_analytic_spring_that_drives_every_scale_lift),
        link = "github.com/Fraktality/spr",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_cubic_spline_morgan_herlocker_mit),
        body = stringResource(R.string.settings_the_natural_cubic_spline_the_animation_curves_ar),
        link = "github.com/morganherlocker/cubic-spline",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_kawarp_better_lyrics_mit),
        body = stringResource(R.string.settings_the_animated_background_blur_noise_warp_and_colo),
        link = "github.com/better-lyrics/kawarp",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_beautiful_lyrics_surfbryce),
        body = stringResource(R.string.settings_prior_art_and_a_reference_point_no_code_from_it),
        link = "github.com/surfbryce/beautiful-lyrics",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_kuromoji_atilika_apache_2_0),
        body = stringResource(R.string.settings_japanese_morphological_analysis_the_only_reason),
        link = "github.com/atilika/kuromoji",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_phrase_pinyin_data_mozillazg_mit),
        body = stringResource(R.string.settings_pinyin_by_the_word_so_chinese_characters_are_rea),
        link = "github.com/mozillazg/phrase-pinyin-data",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_cc_by_sa_4_0_itaigi_cc0_wiktionary_cc_by_sa_4_0),
        body = stringResource(R.string.settings_taiwanese_hokkien_words_and_their_t_i_l_from_the),
        link = "github.com/ChhoeTaigi/ChhoeTaigiDatabase",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_ministry_of_education_r_o_c_cc_by_nd_3_0_tw),
        body = stringResource(R.string.settings_downloaded_only_if_you_ask_for_it_under_language),
        link = "sutian.moe.edu.tw",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_ml_kit_google),
        body = stringResource(R.string.settings_on_device_translation_so_the_lyrics_never_leave),
        link = "developers.google.com/ml-kit",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_amll_ttml_database_amll_dev_and_its_contributors),
        body = stringResource(R.string.settings_the_word_by_word_lyrics_this_app_shows_without_a),
        link = "https://github.com/amll-dev/amll-ttml-db",
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_okhttp_square_apache_2_0_androidx_and_kotlin_apa),
        body = stringResource(R.string.settings_networking_ui_and_language_runtime),
        link = null,
        accent = accent,
    )
    Credit(
        title = stringResource(R.string.settings_lyrics_sources),
        body = stringResource(R.string.settings_the_amll_ttml_database_lrclib_netease_cloud_musi),
        link = null,
        accent = accent,
    )
}

@Composable
private fun Credit(title: String, body: String, link: String?, accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (link != null) {
            Text(
                link,
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Lay a reordered list of visible ids back over the full stored order.
 *
 * The rows on screen are a filtered view — a cache server with developer options off sits in the
 * stored order with no row of its own — so a visible position is not a stored one, and writing
 * the visible list back as-is would delete whatever it does not show. Instead each slot that held
 * a visible id takes the next id the user's new order calls for, and anything hidden stays exactly
 * where it was.
 *
 * Refuses anything that is not a rearrangement of the same ids, so a filtered list that has since
 * changed underneath cannot rewrite the order into something the user did not ask for.
 */
internal fun List<String>.withVisibleOrder(visible: List<String>): List<String> {
    val ids = visible.toSet()
    if (ids.size != visible.size) return this
    if (count { it in ids } != visible.size) return this
    val next = visible.iterator()
    return map { if (it in ids) next.next() else it }
}

/**
 * Open a link in whatever the user browses with.
 *
 * Wrapped rather than called directly: a device with no browser at all throws, and an About
 * screen is not worth crashing over.
 */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---- building blocks ----------------------------------------------------------

/**
 * A collapsible group of settings.
 *
 * The header is always visible and says what is inside; the contents appear when it is
 * tapped. Only one is open at a time, so the sheet is never longer than one section plus a
 * short list of names.
 */
@Composable
private fun Section(
    title: String,
    subtitle: String,
    open: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (open) accent else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            // A chevron drawn as a rotation of one vector, so open and closed cannot drift
            // apart the way two separate icons would.
            val rotation by animateFloatAsState(
                targetValue = if (open) 90f else 0f,
                label = "sectionChevron",
            )
            Icon(
                AppIcons.ChevronRight,
                contentDescription = if (open) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) { content() }
        }
    }
}


@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = Color.White.copy(alpha = 0.08f),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent.copy(alpha = 0.6f),
                checkedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    accent: Color,
    onChange: (Float) -> Unit,
    /** When set, tapping the value types one in, for a slider too coarse to land on it. */
    typed: TypedEntry? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (typed == null) {
                Text(display, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            } else {
                TypedValue(typed, display, accent)
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
        )
    }
}

/**
 * Which language the playing song's Chinese is read in, for when the detector got it wrong.
 *
 * Only shown for a song with Chinese in it. The choice is kept on the phone, and told to the cache
 * server when there is one and the admin key is set — the one thing the app writes there.
 */
/**
 * Download or remove the Ministry of Education's dictionary. Its licence lets it be copied only as it
 * is, so the phone fetches it from the Ministry rather than the app carrying it.
 */
@Composable
private fun HokkienDictionaryRows(dictionary: HokkienDictionary, accent: Color) {
    val status by dictionary.status.collectAsState()
    when (val current = status) {
        HokkienDictionary.Status.Absent, is HokkienDictionary.Status.Failed -> ActionRow(
            title = stringResource(R.string.settings_download_the_ministry_of_education_s_dictionary),
            subtitle = stringResource(R.string.settings_4_5_mb_from_sutian_moe_edu_tw_read_before_the_bu),
            accent = accent,
            onClick = { dictionary.download() },
        )
        HokkienDictionary.Status.Loading -> Hint(stringResource(R.string.settings_loading_the_ministry_of_education_s_dictionary))
        is HokkienDictionary.Status.Downloading -> Hint(
            stringResource(R.string.settings_downloading_the_dictionary, (current.fraction?.let { " — ${(it * 100).toInt()}%" } ?: "…")),
        )
        is HokkienDictionary.Status.Reading -> Hint(stringResource(R.string.settings_reading_the_dictionary, (current.fraction * 100).toInt()))
        is HokkienDictionary.Status.Ready -> ActionRow(
            title = stringResource(R.string.settings_remove_the_ministry_of_education_s_dictionary),
            subtitle = stringResource(R.string.settings_in_use_words_read_differently_or_new, "%,d".format(current.words), "%,d".format(current.changed)),
            accent = accent,
            onClick = { dictionary.remove() },
        )
    }
    (status as? HokkienDictionary.Status.Failed)?.let { Hint(stringResource(R.string.settings_could_not_add_it, it.reason)) }
    Help(
        stringResource(R.string.settings_ministry_of_education_r_o_c_cc_by_nd_3_0_tw_the),
    )
}

@Composable
private fun ReadAsRow(container: AppContainer, settings: Settings, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by container.media.snapshot.collectAsState()
    val lyricsState by container.lyrics.state.collectAsState()
    val key = playing.track?.cacheKey ?: return
    val document = (lyricsState as? LyricsState.Loaded)?.document ?: return

    // What Auto means for this song: the server's tag if it has one, otherwise the detector.
    val auto by produceState<ChineseReading?>(null, document, key, settings.detectHokkien) {
        value = withContext(Dispatchers.Default) {
            val lines = document.lines.map { it.text }
            val corpus = lines.joinToString("\n")
            val chinese = corpus.any { it in '\u4e00'..'\u9fff' } && detectScript(corpus) != Script.JAPANESE
            if (!chinese) return@withContext null
            when (container.lyrics.chineseReading(key, settings.copy(chineseReadings = emptyMap()))) {
                ChineseReading.HOKKIEN -> ChineseReading.HOKKIEN
                ChineseReading.MANDARIN -> ChineseReading.MANDARIN
                ChineseReading.AUTO ->
                    if (HokkienDetector.isHokkien(lines)) ChineseReading.HOKKIEN else ChineseReading.MANDARIN
            }
        }
    }
    val autoReading = auto ?: return
    var note by remember(key) { mutableStateOf<String?>(null) }
    val selected = settings.chineseReadings[key] ?: ChineseReading.AUTO

    ChipGroup(
        label = stringResource(R.string.settings_read_this_song_as),
        options = ChineseReading.entries.map { it to stringResource(it.labelRes) },
        selected = selected,
        accent = accent,
        onSelect = { reading ->
            if (reading == selected) return@ChipGroup
            scope.launch { note = tagNote(context, container.lyrics.setChineseReading(key, reading), reading) }
        },
    )
    Hint(note ?: stringResource(R.string.settings_auto_reads_it_as, stringResource(autoReading.labelRes)))
    Help(
        stringResource(R.string.settings_for_a_song_read_in_the_wrong_language_hokkien_ly),
    )

    val readAs = if (selected == ChineseReading.AUTO) autoReading else selected
    if (readAs == ChineseReading.MANDARIN && document.lines.any { '了' in it.text }) {
        ToggleRow(
            title = stringResource(R.string.settings_sing_as_li_o),
            subtitle = stringResource(R.string.settings_every_particle_in_this_song_not_only_held_ones),
            checked = key in settings.liaoTracks,
            accent = accent,
            onCheckedChange = { container.settings.setSingsLiao(key, it) },
        )
        Help(
            stringResource(R.string.settings_singers_often_sing_as_li_o_rather_than_le_held_n),
        )
    }
}

/** Which source's lyrics to show for the playing track, over the one the ranking picked. */
@Composable
private fun SourceRow(container: AppContainer, settings: Settings, accent: Color) {
    val playing by container.media.snapshot.collectAsState()
    val lyricsState by container.lyrics.state.collectAsState()
    val key = playing.track?.cacheKey ?: return
    val shown = (lyricsState as? LyricsState.Loaded)?.document
    val options by produceState(emptyList<LyricsRepository.SourceOption>(), key, shown) {
        value = container.lyrics.sourceOptions()
    }
    if (options.isEmpty()) return
    val chosen = settings.sourceChoices[key] ?: AUTOMATIC

    ChipGroup(
        label = stringResource(R.string.settings_lyrics_from),
        options = listOf(AUTOMATIC to stringResource(R.string.settings_automatic)) + options.map { option ->
            option.id to if (option.outcome == LyricsCache.Outcome.NONE) stringResource(R.string.settings_none, option.name) else option.name
        },
        selected = chosen,
        accent = accent,
        perRow = 2,
        onSelect = { id -> if (id != chosen) container.lyrics.chooseSource(key, id.takeIf { it != AUTOMATIC }) },
    )
    val shownName = shown?.providerName
    val chosenOption = options.firstOrNull { it.id == chosen }
    Hint(
        when {
            shownName == null -> stringResource(R.string.settings_looking_it_up)
            chosen != AUTOMATIC && shown.providerId != chosen && chosenOption != null -> when (chosenOption.outcome) {
                LyricsCache.Outcome.NONE -> stringResource(R.string.settings_has_nothing_for_this_song_so_the_best_answer_is, chosenOption.name, shownName)
                LyricsCache.Outcome.FAILED -> stringResource(R.string.settings_did_not_answer_so_the_best_answer_is_shown, chosenOption.name, shownName)
                else -> stringResource(R.string.settings_could_not_be_asked_so_the_best_answer_is_shown, chosenOption.name, shownName)
            }
            else -> stringResource(R.string.settings_showing, shownName)
        },
    )
    Help(
        stringResource(R.string.settings_the_best_timing_wins_and_your_order_breaks_ties),
    )
}

private const val AUTOMATIC = ""

private fun tagNote(context: android.content.Context, result: CacheServerProvider.LanguageTag?, reading: ChineseReading): String? {
    val told = if (reading == ChineseReading.AUTO) context.getString(R.string.settings_the_server_decides_for_itself_again) else context.getString(R.string.settings_told_the_server_too)
    return when (result) {
        null, CacheServerProvider.LanguageTag.NO_SERVER -> null
        CacheServerProvider.LanguageTag.SAVED -> told
        CacheServerProvider.LanguageTag.NO_KEY -> context.getString(R.string.settings_kept_on_this_phone_set_the_server_s_admin_key_to)
        CacheServerProvider.LanguageTag.WRONG_KEY -> context.getString(R.string.settings_kept_on_this_phone_the_server_did_not_accept_the)
        CacheServerProvider.LanguageTag.NOT_ADMIN -> context.getString(R.string.settings_kept_on_this_phone_only_the_admin_key_can_tag_so)
        CacheServerProvider.LanguageTag.FAILED -> context.getString(R.string.settings_kept_on_this_phone_the_server_could_not_be_reach)
    }
}

/** A whole number that can be typed as well as slid to: its value, unit, allowed range, and where a new one goes. */
private class TypedEntry(val current: Int, val unit: String, val range: IntRange, val onEnter: (Int) -> Unit)

/**
 * The value of a [SliderRow], boxed like the other text fields to show it can be tapped, and a
 * number field once it is. Each valid number takes effect as it is typed; one outside the range
 * is not accepted.
 */
@Composable
private fun TypedValue(entry: TypedEntry, display: String, accent: Color) {
    var editing by remember { mutableStateOf(false) }
    val box = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Color.White.copy(alpha = 0.07f))
    if (!editing) {
        Text(
            display,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = box.clickable { editing = true }.padding(horizontal = 8.dp, vertical = 3.dp),
        )
        return
    }

    val start = entry.current.toString()
    var text by remember { mutableStateOf(TextFieldValue(start, selection = TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    fun accept(new: TextFieldValue) {
        val number = new.text.toIntOrNull()
        if (!new.text.matches(TYPED_NUMBER) || (number != null && number !in entry.range)) return
        text = new
        number?.let(entry.onEnter)
    }
    Row(box.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        // Compose cannot ask for a signed number pad, and not every keyboard's has a minus key.
        if (entry.range.first < 0) {
            Text(
                "±",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable {
                        val flipped = if (text.text.startsWith("-")) text.text.drop(1) else "-" + text.text
                        accept(TextFieldValue(flipped, selection = TextRange(flipped.length)))
                    }
                    .padding(end = 6.dp),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = ::accept,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .width(48.dp)
                .focusRequester(focus)
                .onFocusChanged {
                    if (focused && !it.isFocused) editing = false
                    focused = it.isFocused
                },
        )
        Text(entry.unit, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** An optional sign and digits, so a half-typed number such as "-" is allowed on the way. */
private val TYPED_NUMBER = Regex("-?\\d{0,6}")

/** One line of a test's results: what was asked, and what it said. */
@Composable
private fun ProbeRow(name: String, outcome: String, working: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            name,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(112.dp),
        )
        Text(
            outcome,
            color = Color.White.copy(alpha = if (working) 0.7f else 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A labelled row of choices — the settings equivalent of a radio group. */
@Composable
private fun <T> ChipGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    accent: Color,
    perRow: Int = 3,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Two options were being squeezed into two thirds of the row and padded with a
        // spacer, which is what cut "Right / bottom" and "Only the cache server" in half.
        // A group narrower than a full row gets the whole row instead.
        val columns = minOf(perRow, options.size).coerceAtLeast(1)
        options.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (value, text) ->
                    ChoiceChip(
                        text = text,
                        selected = value == selected,
                        accent = accent,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            // Two lines, wrapped and centred, rather than one silently cut off. A label
            // that does not fit should get taller, not shorter — a chip reading "From the"
            // says nothing about what it does.
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun StepButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    warn: Boolean,
    enabled: Boolean,
    /** True while this row is the one being dragged. */
    raised: Boolean,
    /** Attached to the grab handle; hold it to drag the row. */
    handle: Modifier,
    accent: Color,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left of the name rather than right of the switch: a handle is for the whole row, and
        // putting it where the row starts keeps every one of them on the same vertical line
        // however long the description underneath runs.
        Box(
            handle.size(38.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.DragHandle,
                contentDescription = stringResource(R.string.settings_reorder, title),
                tint = Color.White.copy(alpha = if (raised) 0.9f else 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = if (warn && enabled) {
                        accent.copy(alpha = 0.85f)
                    } else {
                        Color.White.copy(alpha = 0.5f)
                    },
                    fontSize = 12.sp,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent.copy(alpha = 0.6f),
                checkedThumbColor = Color.White,
            ),
        )
    }
}

/**
 * Settings to a file and back again.
 *
 * Two rows and a switch, because the interesting decision is only ever one question: do the tokens
 * come too? Settings go out as readable JSON, which is what makes a new phone a restore rather than
 * an evening of tapping. Credentials do not, unless asked for and given a passphrase — they are live
 * keys to somebody's own accounts, and the app keeps them out of Android's backup for that reason;
 * writing them into a plain file in Downloads for the convenience of one restore would undo it.
 *
 * The file is chosen through the system picker, so this needs no storage permission and the user
 * decides where it lands.
 */
@Composable
private fun SettingsBackupRows(container: AppContainer, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = container.settings

    var includeTokens by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    /** Held only between picking a file and being told the passphrase for it. */
    var pending by remember { mutableStateOf<String?>(null) }

    fun finish(message: String) {
        status = message
        busy = false
    }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SettingsBackup.write(
                        settings = store.exportableSettings(),
                        credentials = if (includeTokens) store.exportableCredentials() else emptyMap(),
                        passphrase = passphrase.takeIf { includeTokens },
                    )
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } ?: error(context.getString(R.string.settings_could_not_write_there))
                    text.length
                }
            }
            finish(
                result.fold(
                    onSuccess = { size ->
                        val tokens = if (includeTokens) context.getString(R.string.settings_tokens_encrypted) else context.getString(R.string.settings_no_tokens)
                        context.getString(R.string.settings_saved_kb, size / 1024 + 1, tokens)
                    },
                    onFailure = { context.getString(R.string.settings_could_not_save, it.message ?: "unknown error") },
                ),
            )
        }
    }

    fun applyRestore(text: String, key: String?) {
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { SettingsBackup.read(text, key) }
            when (outcome) {
                is SettingsBackup.Restore.Ready -> {
                    store.restore(outcome.settings, outcome.credentials)
                    pending = null
                    val tokens = if (outcome.credentials.isEmpty()) "" else context.getString(R.string.settings_and_its_tokens)
                    finish(context.getString(R.string.settings_restored_settings, outcome.settings.size, tokens))
                }
                SettingsBackup.Restore.NeedsPassphrase -> {
                    pending = text
                    finish(context.getString(R.string.settings_that_file_holds_tokens_type_its_passphrase_then))
                }
                SettingsBackup.Restore.WrongPassphrase ->
                    finish(context.getString(R.string.settings_that_passphrase_does_not_open_the_tokens_in_that))
                SettingsBackup.Restore.NotABackup ->
                    finish(context.getString(R.string.settings_that_is_not_a_melisma_settings_file))
            }
        }
    }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    }
                }.getOrNull()
            }
            if (text == null) {
                finish(context.getString(R.string.settings_could_not_read_that_file))
            } else {
                applyRestore(text, passphrase.takeIf { it.isNotBlank() })
            }
        }
    }

    ToggleRow(
        title = stringResource(R.string.settings_include_your_tokens),
        subtitle = stringResource(R.string.settings_encrypted_under_a_passphrase_you_choose),
        checked = includeTokens,
        accent = accent,
        onCheckedChange = { includeTokens = it },
    )
    Help(
        stringResource(R.string.settings_off_the_file_holds_settings_only_and_is_plain_re),
    )

    if (includeTokens || pending != null) {
        SecretField(
            label = stringResource(R.string.settings_passphrase),
            help = stringResource(R.string.settings_used_to_encrypt_the_tokens_on_the_way_out_and_to),
            value = passphrase,
            accent = accent,
            onChange = { passphrase = it },
        )
    }

    val ready = !busy && (!includeTokens || passphrase.length >= MIN_PASSPHRASE)
    ActionRow(
        title = when {
            busy -> stringResource(R.string.settings_working)
            includeTokens && passphrase.length < MIN_PASSPHRASE ->
                stringResource(R.string.settings_choose_a_passphrase_of_at_least_characters, MIN_PASSPHRASE)
            else -> stringResource(R.string.settings_back_up_settings_to_a_file)
        },
        subtitle = stringResource(R.string.settings_you_choose_where_it_goes),
        accent = accent,
        onClick = {
            if (ready) {
                status = null
                save.launch("melisma-settings-${today()}.json")
            }
        },
    )

    ActionRow(
        title = if (pending != null) stringResource(R.string.settings_restore_with_that_passphrase) else stringResource(R.string.settings_restore_from_a_file),
        subtitle = stringResource(R.string.settings_anything_the_file_does_not_mention_is_left_as_it),
        accent = accent,
        onClick = {
            if (busy) return@ActionRow
            status = null
            val held = pending
            if (held != null) {
                applyRestore(held, passphrase.takeIf { it.isNotBlank() })
            } else {
                // Many providers hand JSON back as octet-stream, so a strict filter hides the very
                // file the user is looking for.
                open.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        },
    )

    status?.let { Hint(it) }
}

/** Long enough that stretching the key is worth doing at all. */
private const val MIN_PASSPHRASE = 8

private fun today(): String {
    val now = java.util.Calendar.getInstance()
    return "%04d-%02d-%02d".format(
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH) + 1,
        now.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

/** A single-line text field for a token, cookie or endpoint. */
@Composable
private fun SecretField(
    label: String,
    help: String,
    value: String,
    accent: Color,
    onChange: (String) -> Unit,
    /** For a value the app produced rather than one the user types. Still selectable, to copy. */
    readOnly: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(
            text = help,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.trim()
                    onChange(text)
                },
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(accent),
                singleLine = true,
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(stringResource(R.string.settings_not_set), color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}
