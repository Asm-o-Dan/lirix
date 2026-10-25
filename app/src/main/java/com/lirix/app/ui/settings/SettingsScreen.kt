package com.lirix.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.ui.blacklist.BlacklistViewModel
import com.lirix.app.ui.i18n.findHostActivity
import com.lirix.app.ui.blacklist.BlacklistSettingsScreen
import com.lirix.app.ui.components.LanguageSelectionDialog
import com.lirix.app.ui.components.ThemeSelectionDialog
import com.lirix.app.ui.i18n.LanguageManager
import com.lirix.app.update.UpdateActivity
import com.lirix.app.ui.theme.LirixThemeTokens

/** Reuses the existing persisted preferences, dialogs and blacklist screen without a media engine. */
@Composable
fun SettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { ThemePreferencesRepository.getInstance(context) }
    val theme by repository.selectedTheme.collectAsState()
    val language by LanguageManager.language(context).collectAsState()
    val version = remember(context.applicationContext) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var blacklistOpen by rememberSaveable { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    BackHandler(enabled = blacklistOpen) { blacklistOpen = false }
    if (blacklistOpen) {
        val db = remember(context.applicationContext) { AppDatabase.getInstance(context) }
        val owner = context.findHostActivity() as ViewModelStoreOwner
        val viewModel = remember(owner, db) { ViewModelProvider(owner, BlacklistViewModel.provideFactory(db))[BlacklistViewModel::class.java] }
        Box(Modifier.fillMaxSize().background(LirixThemeTokens.colors.appBackground)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("settings-blacklist-screen")) {
                BlacklistSettingsScreen(onBack = { blacklistOpen = false }, db = db, viewModel = viewModel)
            }
        }
    } else SettingsScreen(themeName = theme.title, languageName = stringResource(language.titleRes), version = version,
        onTheme = { showTheme = true }, onLanguage = { showLanguage = true },
        onUpdates = { UpdateActivity.open(context) }, onBlacklist = { blacklistOpen = true }, onBack = onBack,
        scrollState = listScroll)
    if (showTheme) ThemeSelectionDialog(theme, { repository.setTheme(it) }, { showTheme = false })
    if (showLanguage) LanguageSelectionDialog(onDismiss = { showLanguage = false })
}

@Composable
fun SettingsScreen(themeName: String, languageName: String, version: String,
    onTheme: () -> Unit, onLanguage: () -> Unit, onUpdates: () -> Unit, onBlacklist: () -> Unit, onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState()) {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.settings_title), modifier = Modifier.weight(1f).testTag("settings-title"),
                    style = MaterialTheme.typography.titleLarge)
            }
        }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).testTag("settings-viewport"), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scrollState)
                .padding(vertical = 12.dp)) {
                SettingsSection(stringResource(R.string.settings_appearance))
                SettingsRow("theme", stringResource(R.string.menu_theme), themeName, Icons.Default.Palette, onTheme)
                SettingsRow("language", stringResource(R.string.menu_language), languageName, Icons.Default.Language, onLanguage)
                HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                SettingsSection(stringResource(R.string.settings_application))
                SettingsRow("updates", stringResource(R.string.settings_updates),
                    stringResource(R.string.settings_version, version), Icons.Default.SystemUpdate, onUpdates)
                SettingsRow("blacklist", stringResource(R.string.menu_blacklist), null, Icons.Default.Shield, onBlacklist)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsRow(id: String, title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("settings-$id")
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, modifier = Modifier.fillMaxWidth().testTag("settings-$id-title"),
                style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, modifier = Modifier.fillMaxWidth().testTag("settings-$id-subtitle"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}
