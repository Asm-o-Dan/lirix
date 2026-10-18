using System.Security.Cryptography;

namespace LirixMediaBridge;

public static class ThumbnailStore
{
    public static async Task<string> SaveAsync(byte[] content, string directory)
    {
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, Convert.ToHexString(SHA256.HashData(content)).ToLowerInvariant() + ".img");
        if (File.Exists(path)) return path;
        var staging = Path.Combine(directory, Guid.NewGuid().ToString("N") + ".tmp");
        try
        {
            await File.WriteAllBytesAsync(staging, content);
            try { File.Move(staging, path); }
            catch (IOException) when (File.Exists(path)) { }
            return path;
        }
        finally { if (File.Exists(staging)) File.Delete(staging); }
    }
}
