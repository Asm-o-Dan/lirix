package com.lirix.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.lirix.app.R
import com.lirix.app.ui.LirixTheme
import com.lirix.app.ui.i18n.LocalizedAppContent
import com.lirix.app.storage.ThemePreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class UpdateActivity : ComponentActivity() {
    private var release by mutableStateOf<AndroidRelease?>(null)
    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf<Float?>(null)
    private var message by mutableStateOf(R.string.update_checking)
    private var downloaded: File? = null
    private var operation: Job? = null
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls()) install()
        else message = R.string.update_permission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalizedAppContent {
                val theme by ThemePreferencesRepository.getInstance(applicationContext).selectedTheme.collectAsState()
                LirixTheme(themeType = theme) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(message))
                            release?.let { Text(stringResource(R.string.update_version, it.version)) }
                            if (busy) {
                                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                                    ?: CircularProgressIndicator()
                                TextButton(onClick = { operation?.cancel() }) { Text(stringResource(R.string.update_cancel)) }
                            } else {
                                release?.let { candidate ->
                                    Button(onClick = { if (downloaded != null) install() else download(candidate) }) {
                                        Text(stringResource(if (downloaded == null) R.string.update_download else R.string.update_install))
                                    }
                                }
                                TextButton(onClick = { checkRelease() }) { Text(stringResource(R.string.update_check)) }
                            }
                            TextButton(onClick = { finish() }) { Text(stringResource(R.string.action_close)) }
                        }
                    }
                }
            }
        }
        checkRelease()
    }

    private fun checkRelease() {
        operation = lifecycleScope.launch {
            busy = true; progress = null; message = R.string.update_checking
            release = null; downloaded = null
            try {
                release = GitHubUpdates.check(this@UpdateActivity)
                message = if (release == null) R.string.update_current else R.string.update_available
            } catch (e: CancellationException) { message = R.string.update_cancelled; throw e }
            catch (_: Exception) { message = R.string.update_failed }
            finally { busy = false }
        }
    }

    private fun download(candidate: AndroidRelease) {
        operation = lifecycleScope.launch {
            busy = true; progress = 0f; message = R.string.update_downloading
            try {
                downloaded = GitHubUpdates.download(this@UpdateActivity, candidate) { amount ->
                    lifecycleScope.launch { progress = amount }
                }
                message = R.string.update_ready
                install()
            } catch (e: CancellationException) { message = R.string.update_cancelled; throw e }
            catch (e: Exception) { message = if (e.message == "SIGNATURE_MISMATCH") R.string.update_signature else R.string.update_failed }
            finally { busy = false; progress = null }
        }
    }

    private fun install() {
        val apk = downloaded ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            message = R.string.update_permission
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: Exception) { message = R.string.update_failed }
            return
        }
        try {
            GitHubUpdates.verify(this, apk)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { message = if (e.message == "SIGNATURE_MISMATCH") R.string.update_signature else R.string.update_failed }
    }

    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, UpdateActivity::class.java))

        /** Foreground-only, throttled checks. No background download or installation without a tap. */
        fun checkOnLaunch(activity: ComponentActivity) {
            val preferences = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - preferences.getLong("checked", 0) in 0 until 24 * 60 * 60 * 1000L) return
            preferences.edit().putLong("checked", now).apply()
            activity.lifecycleScope.launch {
                try {
                    if (GitHubUpdates.check(activity) != null) activity.lifecycle.withResumed { open(activity) }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Manual retry remains available. */ }
            }
        }
    }
}
