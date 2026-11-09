package com.melisma.app.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melisma.app.R
import androidx.compose.ui.res.stringResource

/**
 * What to read before using the app.
 *
 * There is exactly one thing it cannot work without (notification access) and several
 * things it works better with (tokens). Those are easy to confuse, and a lyrics app that
 * silently finds nothing is indistinguishable from a broken one — so this says plainly
 * which is which, once, on the first run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeSheet(
    accent: Color,
    permissionGranted: Boolean,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14141A),
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The sheet is dark whatever the system theme is, so the artwork drawn for a
                // dark surface is named explicitly rather than left to a night qualifier the
                // app would ignore. Its black field blends into the sheet, leaving the mark.
                Image(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(13.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.onboarding_welcome_to_melisma),
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.onboarding_word_by_word_lyrics_for_whatever_your_phone_is_p),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )

            Step(
                number = "1",
                title = stringResource(R.string.onboarding_one_permission_and_it_is_required),
                body = stringResource(R.string.onboarding_android_publishes_what_is_playing_track_artist_a),
                accent = accent,
                done = permissionGranted,
            )

            Step(
                number = "2",
                title = stringResource(R.string.onboarding_no_tokens_needed_for_most_music),
                body = stringResource(R.string.onboarding_four_sources_work_immediately_with_no_account_an),
                accent = accent,
            )

            Step(
                number = "3",
                title = stringResource(R.string.onboarding_tokens_are_optional_and_each_adds_something),
                body = stringResource(R.string.onboarding_all_of_these_live_in_settings_tokens_and_endpoin),
                accent = accent,
            )

            Spacer(Modifier.height(8.dp))

            if (!permissionGranted) {
                PrimaryButton(stringResource(R.string.player_open_notification_access), accent, onGrantPermission)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.onboarding_greyed_out_android_13_wants_one_more_tap_for_an),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
            }
            SecondaryButton(stringResource(R.string.onboarding_add_tokens_in_settings), accent, onOpenSettings)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                if (permissionGranted) stringResource(R.string.onboarding_start_listening) else "Later",
                accent,
                onDismiss,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_you_can_read_this_again_from_settings_about),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(
    number: String,
    title: String,
    body: String,
    accent: Color,
    done: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) accent.copy(alpha = 0.55f) else accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.Black.copy(alpha = 0.86f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
