package com.lirix.app.testfixture;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Synthetic external OS session owned solely by the test APK. No audio or production hooks. */
public final class LookupMediaSessionActivity extends Activity {
    private MediaSession session;
    private TextView status;
    private long durationMs, positionMs, anchorMs;
    private boolean playing;
    private String title, artist;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);
        status = new TextView(this);
        content.addView(status);
        Button finish = new Button(this);
        finish.setText("Finish QA session");
        finish.setOnClickListener(view -> finish());
        content.addView(finish);
        setContentView(content);
        session = new MediaSession(this, "OfflineLookupExternalQa");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { publish(true, currentPosition()); }
            @Override public void onPause() { publish(false, currentPosition()); }
            @Override public void onSeekTo(long position) { publish(playing, position); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
        applyMetadata(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyMetadata(intent);
    }

    private void applyMetadata(Intent intent) {
        title = intent.getStringExtra("title");
        artist = intent.getStringExtra("artist");
        if (title == null || title.trim().isEmpty()) title = "Believer";
        if (artist == null || artist.trim().isEmpty()) artist = "Imagine Dragons";
        durationMs = Math.max(1000L, intent.getLongExtra("durationMs", 204000L));
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs).build());
        publish(true, 0L);
    }

    private long currentPosition() {
        return Math.min(durationMs, positionMs + (playing ? Math.max(0L, SystemClock.elapsedRealtime() - anchorMs) : 0L));
    }

    private void publish(boolean play, long position) {
        playing = play;
        positionMs = Math.max(0L, Math.min(durationMs, position));
        anchorMs = SystemClock.elapsedRealtime();
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO)
            .setState(play ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                positionMs, play ? 1f : 0f, anchorMs).build());
        status.setText("Synthetic MediaSession; no audio\n" + getPackageName() + " UID " + Process.myUid() +
            "\n" + title + " / " + artist + "\n" + (play ? "PLAYING" : "PAUSED") + " at " + positionMs + " ms");
    }

    // Deliberately retain the session onStop while the real Main activity is foregrounded.
    @Override protected void onDestroy() {
        if (session != null) session.release();
        super.onDestroy();
    }
}
