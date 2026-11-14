package com.lirix.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.analytics.ListeningProfile
import com.lirix.app.analytics.ListeningProfileEngine
import com.lirix.app.analytics.ProfileAvatar
import com.lirix.app.analytics.ProfileAvatarGenerator
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.components.ProfileAvatarView
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The avatar the user chose. Stored locally and included in the Android backup; never generated silently. */
class ProfileAvatarStore(private val preferences: SharedPreferences) {
    private val _avatar = MutableStateFlow(ProfileAvatar.decode(preferences.getString(KEY, null)) ?: ProfileAvatar.Neutral)
    val avatar: StateFlow<ProfileAvatar> = _avatar

    fun save(value: ProfileAvatar) {
        preferences.edit().putString(KEY, value.encode()).apply()
        _avatar.value = value
    }

    companion object {
        const val PREFS_NAME = "lirix_profile"
        private const val KEY = "avatar"
        @Volatile private var instance: ProfileAvatarStore? = null
        fun of(context: Context): ProfileAvatarStore = instance ?: synchronized(this) {
            instance ?: ProfileAvatarStore(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

/** Local profile block of the summary screen: the avatar and what is known about listened genres. */
@Composable
fun ProfileCard(tracks: List<TrackEntity>, modifier: Modifier = Modifier) {
    val colors = LirixThemeTokens.colors
    val context = LocalContext.current
    val store = remember(context.applicationContext) { ProfileAvatarStore.of(context) }
    val avatar by store.avatar.collectAsState()
    val metadata by remember(context.applicationContext) { AppDatabase.getInstance(context).trackMetadataDao().observeAllFound() }
        .collectAsState(initial = emptyList())
    val metadataEnabled by remember(context.applicationContext) { com.lirix.app.feature.metadata.TrackMetadataSettings.of(context) }
        .enabled.collectAsState()
    val profile = remember(tracks, metadata) { ListeningProfileEngine.build(tracks, metadata) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.profile_avatar_change)

    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(colors.cardCornerRadius)).background(colors.surfaceCard)
        .border(1.dp, colors.borderSubtle, RoundedCornerShape(colors.cardCornerRadius)).padding(16.dp).testTag("profile-card"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = changeLabel) { editing = true }
            .semantics { contentDescription = changeLabel }.padding(4.dp).testTag("profile-avatar")) {
            ProfileAvatarView(avatar, 72.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(genreSummary(profile, metadataEnabled), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            if (profile.topGenres.isNotEmpty()) Text(
                stringResource(R.string.profile_genre_coverage, profile.tracksWithGenre, profile.tracksTotal),
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    if (editing) ProfileAvatarDialog(initial = avatar, suggestion = ProfileAvatarGenerator.fromListening(profile.avatarInput),
        onSave = { store.save(it); editing = false }, onDismiss = { editing = false })
}

@Composable
private fun genreSummary(profile: ListeningProfile, metadataEnabled: Boolean): String = when {
    profile.topGenres.isNotEmpty() -> stringResource(R.string.profile_top_genres,
        profile.topGenres.map { stringResource(R.string.profile_genre_share, it.genre, it.percent) }.joinToString(", "))
    !metadataEnabled && profile.tracksWithGenre == 0 -> stringResource(R.string.profile_genres_disabled)
    else -> stringResource(R.string.profile_genres_few, profile.tracksWithGenre, ListeningProfileEngine.MIN_TRACKS)
}

@Composable
private fun ProfileAvatarDialog(initial: ProfileAvatar, suggestion: ProfileAvatar, onSave: (ProfileAvatar) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<ProfileAvatar, String>(
        save = { it.encode() }, restore = { ProfileAvatar.decode(it) })) { mutableStateOf(initial) }
    val colors = LirixThemeTokens.colors

    @Composable
    fun Layer(titleRes: Int, count: Int, selected: Int, tag: String, variant: (Int) -> ProfileAvatar) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(count) { index ->
                    val description = stringResource(R.string.profile_avatar_option, stringResource(titleRes), index + 1, count)
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(if (index == selected) 2.dp else 1.dp, if (index == selected) colors.accentPrimary else colors.borderSubtle), RoundedCornerShape(12.dp))
                        .selectable(selected = index == selected, role = Role.RadioButton) { draft = variant(index) }
                        .semantics { contentDescription = description }.testTag("avatar-$tag-$index"),
                        contentAlignment = Alignment.Center) {
                        ProfileAvatarView(variant(index), 40.dp)
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_avatar_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProfileAvatarView(draft, 96.dp) }
                Layer(R.string.profile_avatar_palette, ProfileAvatar.PALETTES, draft.palette, "palette") { draft.copy(palette = it) }
                Layer(R.string.profile_avatar_shape, ProfileAvatar.SHAPES, draft.shape, "shape") { draft.copy(shape = it) }
                Layer(R.string.profile_avatar_pattern, ProfileAvatar.PATTERNS, draft.pattern, "pattern") { draft.copy(pattern = it) }
                Layer(R.string.profile_avatar_accent, ProfileAvatar.ACCENTS, draft.accent, "accent") { draft.copy(accent = it) }
                OutlinedButton(onClick = { draft = suggestion }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("avatar-from-stats")) {
                    Text(stringResource(R.string.profile_avatar_from_stats))
                }
                Text(stringResource(R.string.profile_avatar_from_stats_hint), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}
