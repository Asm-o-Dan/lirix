using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace LirixMediaBridge;

public sealed class SessionMessage
{
    [JsonPropertyName("type")]
    public string Type => "session";

    [JsonPropertyName("app")]
    public string? App { get; set; }

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("artist")]
    public string? Artist { get; set; }

    [JsonPropertyName("album")]
    public string? Album { get; set; }

    [JsonPropertyName("durationMs")]
    public long DurationMs { get; set; }

    [JsonPropertyName("positionMs")]
    public long PositionMs { get; set; }

    [JsonPropertyName("isPlaying")]
    public bool IsPlaying { get; set; }

    [JsonPropertyName("thumbnailPath")]
    public string? ThumbnailPath { get; set; }
}

public sealed class TimelineMessage
{
    [JsonPropertyName("type")]
    public string Type => "timeline";

    [JsonPropertyName("positionMs")]
    public long PositionMs { get; set; }
}

public sealed class ClosedMessage
{
    [JsonPropertyName("type")]
    public string Type => "closed";
}

public sealed class CommandMessage
{
    [JsonPropertyName("cmd")]
    public string? Cmd { get; set; }

    [JsonPropertyName("positionMs")]
    public long? PositionMs { get; set; }
}

[JsonSourceGenerationOptions(
    WriteIndented = false,
    DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    PropertyNamingPolicy = JsonKnownNamingPolicy.CamelCase
)]
[JsonSerializable(typeof(SessionMessage))]
[JsonSerializable(typeof(TimelineMessage))]
[JsonSerializable(typeof(ClosedMessage))]
[JsonSerializable(typeof(CommandMessage))]
public partial class BridgeJsonContext : JsonSerializerContext
{
    public static readonly BridgeJsonContext Relaxed = new(new JsonSerializerOptions
    {
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        WriteIndented = false,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    });
}
