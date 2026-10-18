namespace LirixMediaBridge;

// Call under the watcher's state lock. All elapsed time uses a monotonic clock.
public sealed class TimelineAnchor
{
    private long _position, _at, _duration;
    private bool _playing, _hasSnapshot;
    private long _snapshotPosition;
    private DateTimeOffset _updated;
    private DateTimeOffset _seekBarrier;
    private long _undatedSeekGraceUntil;

    public long Position(long monotonicMs) => Math.Clamp(
        _position + (_playing ? Math.Max(0, monotonicMs - _at) : 0),
        0, _duration > 0 ? _duration : long.MaxValue);

    public void Observe(long position, long duration, DateTimeOffset updatedUtc,
        bool isPlaying, DateTimeOffset utcNow, long monotonicMs)
    {
        // Undated providers cannot prove freshness. Briefly retain the accepted
        // command while their snapshot catches up, then permit external seeking.
        var fresh = updatedUtc == default
            ? monotonicMs >= _undatedSeekGraceUntil
            : updatedUtc > _seekBarrier;
        var changed = fresh && (!_hasSnapshot ||
            (updatedUtc == default
                ? position != _snapshotPosition
                : updatedUtc > _updated ||
                  (updatedUtc == _updated && position != _snapshotPosition)));
        var current = Position(monotonicMs);
        _duration = Math.Max(0, duration);
        if (changed)
        {
            // GSMTC Position is sampled at LastUpdatedTime, not at this poll.
            var age = updatedUtc == default ? 0 : Math.Max(0, (utcNow - updatedUtc).TotalMilliseconds);
            _position = Math.Max(0, position) + (isPlaying ? (long)Math.Min(age, int.MaxValue) : 0);
            _updated = updatedUtc;
            _snapshotPosition = position;
            _hasSnapshot = true;
        }
        else _position = current;
        _at = monotonicMs;
        _playing = isPlaying;
    }

    public void SeekAccepted(long position, long monotonicMs)
    {
        _position = Math.Clamp(position, 0, _duration > 0 ? _duration : long.MaxValue);
        _at = monotonicMs;
        _undatedSeekGraceUntil = monotonicMs + 1500;
    }

    public void ApplySeek(bool accepted, long position, long monotonicMs)
    {
        if (accepted) SeekAccepted(position, monotonicMs);
    }

    public void ApplySeek(bool accepted, long position, long monotonicMs, DateTimeOffset acceptedUtc)
    {
        if (!accepted) return;
        SeekAccepted(position, monotonicMs);
        _seekBarrier = acceptedUtc;
    }
}
