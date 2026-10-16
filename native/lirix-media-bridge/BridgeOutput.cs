using System.Text.Json;
using System.Text.Json.Serialization.Metadata;

namespace LirixMediaBridge;

public static class BridgeOutput
{
    private static readonly object _stdoutLock = new();

    public static void SendJson<T>(T message, JsonTypeInfo<T> typeInfo)
    {
        try
        {
            lock (_stdoutLock)
            {
                var json = JsonSerializer.Serialize(message, typeInfo);
                Console.Out.WriteLine(json);
                Console.Out.Flush();
            }
        }
        catch { }
    }
}
