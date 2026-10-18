using System.Diagnostics;
using System.Text.Json.Serialization.Metadata;
using Windows.Media.Control;
using Windows.Storage.Streams;

namespace LirixMediaBridge;

public sealed class GsmtcWatcher : IDisposable
{
    private readonly object _stateLock = new();
    private TimelineAnchor _clock = new();
    private readonly GenerationGate _metadataRevision = new();
    private readonly GenerationGate _seekRevision = new();
    private readonly GenerationGate _managerRevision = new();
    private bool _disposed;
    private long _publishedRevision;
    private bool _selectionPending;
    private static long NowMs => (long)(Stopwatch.GetTimestamp() * 1000.0 / Stopwatch.Frequency);

    private GlobalSystemMediaTransportControlsSessionManager? _manager;
    private GlobalSystemMediaTransportControlsSession? _currentSession;
    private readonly SemaphoreSlim _sessionLock = new(1, 1);

    private readonly Timer _timelineTimer;
    private long _durationMs;
    private bool _isPlaying;

    public GsmtcWatcher()
    {
        _timelineTimer = new Timer(OnTimelineTimerTick, null, Timeout.Infinite, Timeout.Infinite);
    }

    public async Task StartAsync()
    {
        try
        {
            _manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            if (_manager != null)
            {
                _manager.CurrentSessionChanged += OnManagerCurrentSessionChanged;
                _manager.SessionsChanged += OnManagerSessionsChanged;

                await RefreshSessionAsync();
            }
            else
            {
                SendClosed();
            }
        }
        catch (Exception)
        {
            SendClosed();
        }
    }

    private void OnManagerCurrentSessionChanged(GlobalSystemMediaTransportControlsSessionManager sender, CurrentSessionChangedEventArgs args)
    {
        lock (_stateLock) { _selectionPending = true; _managerRevision.Next(); _metadataRevision.Next(); _seekRevision.Next(); }
        _ = RefreshSessionAsync();
    }

    private void OnManagerSessionsChanged(GlobalSystemMediaTransportControlsSessionManager sender, SessionsChangedEventArgs args)
    {
        lock (_stateLock) { _selectionPending = true; _managerRevision.Next(); _metadataRevision.Next(); _seekRevision.Next(); }
        _ = RefreshSessionAsync();
    }

    private async Task RefreshSessionAsync()
    {
        await _sessionLock.WaitAsync();
        try
        {
            if (_manager == null)
            {
                SendClosed();
                return;
            }

            long managerRevision;
            lock (_stateLock)
            {
                if (_disposed) return;
                managerRevision = _managerRevision.Current;
            }
            var newSession = _manager.GetCurrentSession();
            if (newSession == null)
            {
                var sessions = _manager.GetSessions();
                newSession = sessions.Count > 0 ? sessions[0] : null;
            }

            if (newSession != null && ReferenceEquals(newSession, _currentSession))
            {
                lock (_stateLock)
                {
                    if (_disposed || !_managerRevision.IsCurrent(managerRevision)) return;
                    _selectionPending = false;
                }
                await EmitFullSessionStateAsync();
                return;
            }

            lock (_stateLock)
            {
                if (_disposed || !_managerRevision.IsCurrent(managerRevision)) return;
                // Unsubscribe old session
                if (_currentSession != null)
                {
                    try
                    {
                        _currentSession.MediaPropertiesChanged -= OnMediaPropertiesChanged;
                        _currentSession.PlaybackInfoChanged -= OnPlaybackInfoChanged;
                        _currentSession.TimelinePropertiesChanged -= OnTimelinePropertiesChanged;
                    }
                    catch { }
                }

                _currentSession = newSession;
                _selectionPending = false;
                _metadataRevision.Next();
                _seekRevision.Next();
                _clock = new TimelineAnchor();
                _isPlaying = false;
                _durationMs = 0;
                _publishedRevision = 0;

                if (_currentSession == null)
                {
                    _isPlaying = false;
                    _timelineTimer.Change(Timeout.Infinite, Timeout.Infinite);
                    SendClosed();
                    return;
                }

                _currentSession.MediaPropertiesChanged += OnMediaPropertiesChanged;
                _currentSession.PlaybackInfoChanged += OnPlaybackInfoChanged;
                _currentSession.TimelinePropertiesChanged += OnTimelinePropertiesChanged;

            }

            await EmitFullSessionStateAsync();
        }
        catch (Exception)
        {
            SendClosed();
        }
        finally
        {
            _sessionLock.Release();
        }
    }

    private void OnMediaPropertiesChanged(GlobalSystemMediaTransportControlsSession sender, MediaPropertiesChangedEventArgs args)
    {
        if (ReferenceEquals(sender, _currentSession)) _ = EmitFullSessionStateAsync();
    }

    private void OnPlaybackInfoChanged(GlobalSystemMediaTransportControlsSession sender, PlaybackInfoChangedEventArgs args)
    {
        if (ReferenceEquals(sender, _currentSession))
        {
            _ = UpdateTimelineAsync();
            _ = EmitFullSessionStateAsync();
        }
    }

    private void OnTimelinePropertiesChanged(GlobalSystemMediaTransportControlsSession sender, TimelinePropertiesChangedEventArgs args)
    {
        if (ReferenceEquals(sender, _currentSession)) _ = UpdateTimelineAsync();
    }

    private async Task EmitFullSessionStateAsync()
    {
        GlobalSystemMediaTransportControlsSession? session;
        long revision;
        lock (_stateLock)
        {
            if (_selectionPending || _disposed) return;
            session = _currentSession;
            revision = _metadataRevision.Next();
        }
        if (session == null) return;

        try
        {
            var mediaProps = await session.TryGetMediaPropertiesAsync();
            var playbackInfo = session.GetPlaybackInfo();
            var timeline = session.GetTimelineProperties();

            var appName = CleanAppName(session.SourceAppUserModelId);
            var title = mediaProps?.Title ?? string.Empty;
            var artist = mediaProps?.Artist ?? string.Empty;
            var album = mediaProps?.AlbumTitle ?? string.Empty;

            var status = playbackInfo?.PlaybackStatus ?? GlobalSystemMediaTransportControlsSessionPlaybackStatus.Closed;
            var isPlaying = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
            var thumbPath = mediaProps?.Thumbnail == null ? null : await SaveThumbnailAsync(mediaProps.Thumbnail);

            lock (_stateLock)
            {
                if (_disposed || _selectionPending || !_metadataRevision.IsCurrent(revision) || !ReferenceEquals(session, _currentSession)) return;
                // Read again after thumbnail I/O: a seek may have happened meanwhile.
                playbackInfo = session.GetPlaybackInfo();
                timeline = session.GetTimelineProperties();
                isPlaying = playbackInfo?.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
                ObserveTimeline(timeline, isPlaying);
                var posMs = _clock.Position(NowMs);
                var durMs = _durationMs;

                var sessionMsg = new SessionMessage
                {
                    App = appName,
                    Title = title,
                    Artist = artist,
                    Album = album,
                    DurationMs = durMs,
                    PositionMs = posMs,
                    IsPlaying = isPlaying,
                    ThumbnailPath = thumbPath
                };

                SendJson(sessionMsg, BridgeJsonContext.Relaxed.SessionMessage);
                _publishedRevision = revision;

                // Keep polling when paused: not all sources emit seek events.
                _timelineTimer.Change(1000, 1000);
            }
        }
        catch (Exception)
        {
            // Session may have been torn down
        }
    }

    private void ObserveTimeline(GlobalSystemMediaTransportControlsSessionTimelineProperties? timeline, bool playing)
    {
        _isPlaying = playing;
        if (timeline != null)
        {
            _durationMs = Math.Max(0, (long)timeline.EndTime.TotalMilliseconds);
            _clock.Observe((long)timeline.Position.TotalMilliseconds, _durationMs,
                timeline.LastUpdatedTime, playing, DateTimeOffset.UtcNow, NowMs);
        }
        _timelineTimer.Change(1000, 1000);
    }

    private Task UpdateTimelineAsync()
    {
        lock (_stateLock)
        {
            var session = _currentSession;
            if (_disposed || _selectionPending || session == null) return Task.CompletedTask;
            try
            {
                var playing = session.GetPlaybackInfo()?.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
                ObserveTimeline(session.GetTimelineProperties(), playing);
                if (_metadataRevision.IsCurrent(_publishedRevision))
                    SendJson(new TimelineMessage { PositionMs = _clock.Position(NowMs) }, BridgeJsonContext.Relaxed.TimelineMessage);
            }
            catch { }
        }
        return Task.CompletedTask;
    }

    private void OnTimelineTimerTick(object? state) => _ = UpdateTimelineAsync();

    public async Task ExecuteCommandAsync(CommandMessage cmd)
    {
        var session = _currentSession ?? _manager?.GetCurrentSession();
        if (session == null) return;

        try
        {
            switch (cmd.Cmd?.ToLowerInvariant())
            {
                case "playpause":
                    await session.TryTogglePlayPauseAsync();
                    break;
                case "play":
                    await session.TryPlayAsync();
                    break;
                case "pause":
                    await session.TryPauseAsync();
                    break;
                case "seek":
                    if (cmd.PositionMs.HasValue)
                    {
                        var seekRevision = _seekRevision.Next();
                        long target;
                        lock (_stateLock)
                        {
                            target = Math.Clamp(cmd.PositionMs.Value, 0,
                                _durationMs > 0 ? _durationMs : TimeSpan.MaxValue.Ticks / TimeSpan.TicksPerMillisecond);
                        }
                        var ticks = target * TimeSpan.TicksPerMillisecond;
                        var accepted = await session.TryChangePlaybackPositionAsync(ticks);
                        lock (_stateLock)
                        {
                            if (!_disposed && !_selectionPending && ReferenceEquals(session, _currentSession) && _seekRevision.IsCurrent(seekRevision) && accepted)
                            {
                                _clock.ApplySeek(true, target, NowMs, DateTimeOffset.UtcNow);
                                SendJson(new TimelineMessage { PositionMs = _clock.Position(NowMs) }, BridgeJsonContext.Relaxed.TimelineMessage);
                            }
                        }
                    }
                    break;
                case "skipnext":
                    await session.TrySkipNextAsync();
                    break;
                case "skipprevious":
                    await session.TrySkipPreviousAsync();
                    break;
            }
        }
        catch { }
    }

    private static async Task<string?> SaveThumbnailAsync(IRandomAccessStreamReference? thumbnailRef)
    {
        if (thumbnailRef == null) return null;

        try
        {
            using var stream = await thumbnailRef.OpenReadAsync();
            if (stream == null || stream.Size == 0) return null;

            if (stream.Size > 16 * 1024 * 1024) return null;
            using var reader = new DataReader(stream.GetInputStreamAt(0));
            await reader.LoadAsync((uint)stream.Size);
            byte[] buffer = new byte[stream.Size];
            reader.ReadBytes(buffer);
            return await ThumbnailStore.SaveAsync(buffer, Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Lirix", "cache", "thumbnails"));
        }
        catch
        {
            return null;
        }
    }

    private static string CleanAppName(string? id)
    {
        if (string.IsNullOrWhiteSpace(id)) return "Unknown";
        if (id.Contains('\\') || id.Contains('/'))
        {
            return Path.GetFileName(id);
        }
        if (id.Contains("Spotify", StringComparison.OrdinalIgnoreCase)) return "Spotify.exe";
        if (id.Contains("Telegram", StringComparison.OrdinalIgnoreCase)) return "Telegram.exe";
        if (id.Contains("Chrome", StringComparison.OrdinalIgnoreCase)) return "chrome.exe";
        if (id.Contains("YandexMusic", StringComparison.OrdinalIgnoreCase) || id.Contains("Yandex.Music", StringComparison.OrdinalIgnoreCase)) return "YandexMusic.exe";
        return id;
    }

    private static void SendClosed()
    {
        BridgeOutput.SendJson(new ClosedMessage(), BridgeJsonContext.Relaxed.ClosedMessage);
    }

    private static void SendJson<T>(T message, JsonTypeInfo<T> typeInfo)
    {
        BridgeOutput.SendJson(message, typeInfo);
    }

    public void Dispose()
    {
        lock (_stateLock)
        {
            _disposed = true;
            _metadataRevision.Next();
            _seekRevision.Next();
        }
        _timelineTimer.Dispose();

        if (_currentSession != null)
        {
            try
            {
                _currentSession.MediaPropertiesChanged -= OnMediaPropertiesChanged;
                _currentSession.PlaybackInfoChanged -= OnPlaybackInfoChanged;
                _currentSession.TimelinePropertiesChanged -= OnTimelinePropertiesChanged;
            }
            catch { }
        }

        if (_manager != null)
        {
            try
            {
                _manager.CurrentSessionChanged -= OnManagerCurrentSessionChanged;
                _manager.SessionsChanged -= OnManagerSessionsChanged;
            }
            catch { }
        }

        _sessionLock.Dispose();
    }
}
