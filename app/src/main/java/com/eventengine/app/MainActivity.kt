package com.eventengine.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.eventengine.app.classifier.ShareIntentClassifier
import com.eventengine.app.classifier.SharedMediaPayload
import com.eventengine.app.feature.MusicLyricsNotificationManager
import com.eventengine.app.feature.ParsedTrackInfo
import com.eventengine.app.ui.AppScaffold
import com.eventengine.app.ui.EventEngineTheme
import timber.log.Timber

/**
 * Main host activity for the Jetpack Compose EventEngine application.
 */
class MainActivity : ComponentActivity() {

    private var initialLyricsTrack by mutableStateOf<ParsedTrackInfo?>(null)
    private var sharedPayload by mutableStateOf<SharedMediaPayload?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.i("POST_NOTIFICATIONS granted")
        } else {
            Timber.w("POST_NOTIFICATIONS denied by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+ (Poco M7)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            EventEngineTheme {
                AppScaffold(
                    initialLyricsTrack = initialLyricsTrack,
                    onLyricsDialogConsumed = { initialLyricsTrack = null },
                    initialSharedPayload = sharedPayload,
                    onSharedPayloadConsumed = { sharedPayload = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == MusicLyricsNotificationManager.ACTION_VIEW_LYRICS) {
            val title = intent.getStringExtra(MusicLyricsNotificationManager.EXTRA_TRACK_TITLE).orEmpty()
            val artist = intent.getStringExtra(MusicLyricsNotificationManager.EXTRA_TRACK_ARTIST).orEmpty()
            val album = intent.getStringExtra(MusicLyricsNotificationManager.EXTRA_TRACK_ALBUM).orEmpty()
            if (title.isNotBlank()) {
                initialLyricsTrack = ParsedTrackInfo(title = title, artist = artist, album = album)
                Timber.i("Received ACTION_VIEW_LYRICS for: %s - %s", artist, title)
            }
        } else if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!rawText.isNullOrBlank()) {
                sharedPayload = ShareIntentClassifier.classifySharedText(rawText)
                Timber.i("Received ACTION_SEND payload: %s", sharedPayload)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.eventengine.app.ingestion.NotificationListener.isPermissionGranted(this)) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, com.eventengine.app.ingestion.NotificationListener::class.java)
                )
            } catch (e: Exception) {
                // Log or ignore if already bound
            }
        }
    }
}
