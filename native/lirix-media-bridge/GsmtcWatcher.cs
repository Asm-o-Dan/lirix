using System.Diagnostics;
using System.Text.Json;
using System.Text.Json.Serialization.Metadata;
using Windows.Media.Control;
using Windows.Storage.Streams;

namespace LirixMediaBridge;

public sealed class GsmtcWatcher : IDisposable
{
    private static readonly object _stdoutLock = new();

    private GlobalSystemMediaTransportControlsSessionManager? _manager;
    private GlobalSystemMediaTransportControlsSession? _currentSession;
    private readonly SemaphoreSlim _sessionLock = new(1, 1);

    private readonly Timer _timelineTimer;
    private long _baselinePositionMs;
    private long _lastPositionTimestamp;
    private long _durationMs;
    private bool _isPlaying;

    private string? _lastTrackKey;
    private string? _cachedThumbnailPath;

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
        _ = RefreshSessionAsync();
    }

    private void OnManagerSessionsChanged(GlobalSystemMediaTransportControlsSessionManager sender, SessionsChangedEventArgs args)
    {
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

            var newSession = _manager.GetCurrentSession();
            if (newSession == null)
            {
                var sessions = _manager.GetSessions();
                newSession = sessions.Count > 0 ? sessions[0] : null;
            }

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
        _ = EmitFullSessionStateAsync();
    }

    private void OnPlaybackInfoChanged(GlobalSystemMediaTransportControlsSession sender, PlaybackInfoChangedEventArgs args)
    {
        _ = EmitFullSessionStateAsync();
    }

    private void OnTimelinePropertiesChanged(GlobalSystemMediaTransportControlsSession sender, TimelinePropertiesChangedEventArgs args)
    {
        _ = UpdateTimelineAsync();
    }

    private async Task EmitFullSessionStateAsync()
    {
        var session = _currentSession;
        if (session == null)
        {
            SendClosed();
            return;
        }

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
            _isPlaying = isPlaying;

            long posMs = 0;
            long durMs = 0;
            if (timeline != null)
            {
                posMs = (long)timeline.Position.TotalMilliseconds;
                durMs = (long)timeline.EndTime.TotalMilliseconds;
            }

            _baselinePositionMs = posMs;
            _lastPositionTimestamp = Stopwatch.GetTimestamp();
            _durationMs = durMs;

            // Thumbnail caching
            string? thumbPath = null;
            if (mediaProps?.Thumbnail != null)
            {
                var trackKey = $"{appName}|{title}|{artist}";
                if (trackKey == _lastTrackKey && !string.IsNullOrEmpty(_cachedThumbnailPath) && File.Exists(_cachedThumbnailPath))
                {
                    thumbPath = _cachedThumbnailPath;
                }
                else
                {
                    thumbPath = await SaveThumbnailAsync(mediaProps.Thumbnail);
                    _lastTrackKey = trackKey;
                    _cachedThumbnailPath = thumbPath;
                }
            }

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

            // Configure periodic timer for timeline extrapolation while playing
            if (_isPlaying && durMs > 0)
            {
                _timelineTimer.Change(1000, 1000);
            }
            else
            {
                _timelineTimer.Change(Timeout.Infinite, Timeout.Infinite);
            }
        }
        catch (Exception)
        {
            // Session may have been torn down
        }
    }

    private Task UpdateTimelineAsync()
    {
        var session = _currentSession;
        if (session == null) return Task.CompletedTask;

        try
        {
            var timeline = session.GetTimelineProperties();
            if (timeline != null)
            {
                var posMs = (long)timeline.Position.TotalMilliseconds;
                _baselinePositionMs = posMs;
                _lastPositionTimestamp = Stopwatch.GetTimestamp();
                _durationMs = (long)timeline.EndTime.TotalMilliseconds;

                SendJson(new TimelineMessage { PositionMs = posMs }, BridgeJsonContext.Relaxed.TimelineMessage);
            }
        }
        catch { }

        return Task.CompletedTask;
    }

    private void OnTimelineTimerTick(object? state)
    {
        if (!_isPlaying || _currentSession == null || _durationMs <= 0) return;

        try
        {
            var elapsedMs = (long)((Stopwatch.GetTimestamp() - _lastPositionTimestamp) * 1000.0 / Stopwatch.Frequency);
            var estimatedPos = _baselinePositionMs + elapsedMs;
            if (estimatedPos > _durationMs) estimatedPos = _durationMs;

            SendJson(new TimelineMessage { PositionMs = estimatedPos }, BridgeJsonContext.Relaxed.TimelineMessage);
        }
        catch { }
    }

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
                        var ticks = TimeSpan.FromMilliseconds(cmd.PositionMs.Value).Ticks;
                        await session.TryChangePlaybackPositionAsync(ticks);
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

            var tempPath = Path.Combine(Path.GetTempPath(), "lirix_thumb.png");
            using var reader = new DataReader(stream.GetInputStreamAt(0));
            await reader.LoadAsync((uint)stream.Size);

            byte[] buffer = new byte[stream.Size];
            reader.ReadBytes(buffer);

            using var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite);
            await fileStream.WriteAsync(buffer);
            await fileStream.FlushAsync();

            return tempPath;
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
