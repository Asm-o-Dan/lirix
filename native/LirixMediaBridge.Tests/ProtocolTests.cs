using System.Text.Json;
using LirixMediaBridge;
using Xunit;

namespace LirixMediaBridge.Tests;

public class ProtocolTests
{
    [Fact]
    public void SessionMessage_SerializesCorrectly()
    {
        var msg = new SessionMessage
        {
            App = "Spotify.exe",
            Title = "Песня",
            Artist = "Артист",
            Album = "Альбом",
            DurationMs = 180000,
            PositionMs = 15000,
            IsPlaying = true,
            ThumbnailPath = @"C:\Users\User\AppData\Local\Temp\lirix_thumb.png"
        };

        var json = JsonSerializer.Serialize(msg, BridgeJsonContext.Relaxed.SessionMessage);

        Assert.Contains("\"type\":\"session\"", json);
        Assert.Contains("\"app\":\"Spotify.exe\"", json);
        Assert.Contains("\"title\":\"Песня\"", json);
        Assert.Contains("\"artist\":\"Артист\"", json);
        Assert.Contains("\"album\":\"Альбом\"", json);
        Assert.Contains("\"durationMs\":180000", json);
        Assert.Contains("\"positionMs\":15000", json);
        Assert.Contains("\"isPlaying\":true", json);
        Assert.Contains("\"thumbnailPath\":", json);
    }

    [Fact]
    public void TimelineMessage_SerializesCorrectly()
    {
        var msg = new TimelineMessage
        {
            PositionMs = 16000
        };

        var json = JsonSerializer.Serialize(msg, BridgeJsonContext.Default.TimelineMessage);

        Assert.Equal("{\"type\":\"timeline\",\"positionMs\":16000}", json);
    }

    [Fact]
    public void ClosedMessage_SerializesCorrectly()
    {
        var msg = new ClosedMessage();
        var json = JsonSerializer.Serialize(msg, BridgeJsonContext.Default.ClosedMessage);

        Assert.Equal("{\"type\":\"closed\"}", json);
    }

    [Theory]
    [InlineData("{\"cmd\":\"playPause\"}", "playPause", null)]
    [InlineData("{\"cmd\":\"skipNext\"}", "skipNext", null)]
    [InlineData("{\"cmd\":\"skipPrevious\"}", "skipPrevious", null)]
    [InlineData("{\"cmd\":\"seek\",\"positionMs\":120000}", "seek", 120000L)]
    public void CommandMessage_DeserializesCorrectly(string inputJson, string expectedCmd, long? expectedPos)
    {
        var cmd = JsonSerializer.Deserialize(inputJson, BridgeJsonContext.Default.CommandMessage);

        Assert.NotNull(cmd);
        Assert.Equal(expectedCmd, cmd.Cmd);
        Assert.Equal(expectedPos, cmd.PositionMs);
    }
}
