using Xunit;

namespace LirixMediaBridge.Tests;

public class TimelineAnchorTests
{
    private static readonly DateTimeOffset SnapshotTime = new(2026, 10, 18, 12, 0, 0, TimeSpan.Zero);

    [Fact]
    public void PlayingSnapshot_AccountsForItsAge_AndAdvancesMonotonically()
    {
        var anchor = new TimelineAnchor();
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime.AddSeconds(2), 10_000);

        Assert.Equal(9_000, anchor.Position(10_000));
        Assert.Equal(10_500, anchor.Position(11_500));
    }

    [Fact]
    public void RepeatedSnapshot_DoesNotFreezeOrDoubleCountElapsedTime()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime.AddSeconds(1), 11_000);
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime.AddSeconds(2), 12_000);

        Assert.Equal(9_000, anchor.Position(12_000));
        Assert.Equal(10_000, anchor.Position(13_000));
    }

    [Fact]
    public void PauseAndResumeWithUnchangedSnapshot_PreserveTheClockContinuity()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.Observe(7_000, 293_000, SnapshotTime, false, SnapshotTime.AddSeconds(1), 11_000);
        Assert.Equal(8_000, anchor.Position(15_000));
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime.AddSeconds(5), 15_000);
        Assert.Equal(9_000, anchor.Position(16_000));
    }

    [Fact]
    public void AcceptedSeek_JumpsImmediately_AndDoesNotWaitForSourceEvent()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 10_100);

        Assert.Equal(56_000, anchor.Position(10_100));
        Assert.Equal(57_000, anchor.Position(11_100));
    }

    [Fact]
    public void RejectedSeek_PreservesTheExistingClock()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(false, 56_000, 10_100);

        Assert.Equal(7_100, anchor.Position(10_100));
        Assert.Equal(8_100, anchor.Position(11_100));
    }

    [Fact]
    public void UnchangedSourceSnapshot_CannotRewindAnAcceptedSeek()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 10_100);
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime.AddSeconds(1), 11_000);

        Assert.Equal(56_900, anchor.Position(11_000));
    }

    [Fact]
    public void NewerExternalSeek_ReplacesLocalAnchor()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 10_100);
        anchor.Observe(120_000, 293_000, SnapshotTime.AddSeconds(2), true, SnapshotTime.AddSeconds(2), 12_000);

        Assert.Equal(120_000, anchor.Position(12_000));
        Assert.Equal(120_500, anchor.Position(12_500));
    }

    [Fact]
    public void DelayedSnapshotFromBeforeSeek_CannotRewindTheAcceptedSeek()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 13_000, SnapshotTime.AddSeconds(3));
        anchor.Observe(9_000, 293_000, SnapshotTime.AddSeconds(2), true, SnapshotTime.AddSeconds(4), 14_000);

        Assert.Equal(57_000, anchor.Position(14_000));
    }

    [Fact]
    public void SnapshotAfterSeekBarrier_CanReportAnExternalBackwardSeek()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 120_000, 13_000, SnapshotTime.AddSeconds(3));
        anchor.Observe(20_000, 293_000, SnapshotTime.AddSeconds(4), true, SnapshotTime.AddSeconds(4), 14_000);

        Assert.Equal(20_000, anchor.Position(14_000));
    }

    [Fact]
    public void UndatedChangedSnapshotDuringSeekGrace_CannotImmediatelyRewindSeek()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 13_000, SnapshotTime.AddSeconds(3));
        anchor.Observe(9_000, 293_000, default, true, SnapshotTime.AddSeconds(4), 14_000);

        Assert.Equal(57_000, anchor.Position(14_000));
    }

    [Fact]
    public void UndatedChangedExternalSeekAfterGrace_IsReconciled()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, 56_000, 13_000, SnapshotTime.AddSeconds(3));
        anchor.Observe(20_000, 293_000, default, true, SnapshotTime.AddSeconds(5), 15_000);

        Assert.Equal(20_000, anchor.Position(15_000));
    }

    [Fact]
    public void PausedExternalSeek_UpdatesPosition_WithoutExtrapolating()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.Observe(120_000, 293_000, SnapshotTime.AddSeconds(2), false, SnapshotTime.AddSeconds(4), 14_000);

        Assert.Equal(120_000, anchor.Position(14_000));
        Assert.Equal(120_000, anchor.Position(20_000));
    }

    [Fact]
    public void Position_RemainsWithinTrackBounds()
    {
        var anchor = PlayingAtSevenSeconds();
        anchor.ApplySeek(true, -1_000, 10_000);
        Assert.Equal(0, anchor.Position(10_000));
        anchor.ApplySeek(true, 400_000, 10_000);
        Assert.Equal(293_000, anchor.Position(10_000));
        Assert.Equal(293_000, anchor.Position(20_000));
    }

    private static TimelineAnchor PlayingAtSevenSeconds()
    {
        var anchor = new TimelineAnchor();
        anchor.Observe(7_000, 293_000, SnapshotTime, true, SnapshotTime, 10_000);
        return anchor;
    }
}
