using Xunit;

namespace LirixMediaBridge.Tests;

public sealed class ThumbnailStoreTests : IDisposable
{
    private readonly string _directory = Path.Combine(Path.GetTempPath(), "lirix-thumbnail-tests", Guid.NewGuid().ToString("N"));

    [Fact]
    public async Task DifferentTrackImages_HaveDifferentImmutablePaths()
    {
        byte[] trackA = [137, 80, 78, 71, 1, 2, 3];
        byte[] trackB = [137, 80, 78, 71, 4, 5, 6];
        var first = await ThumbnailStore.SaveAsync(trackA, _directory);
        var second = await ThumbnailStore.SaveAsync(trackB, _directory);

        Assert.NotEqual(first, second);
        Assert.Equal(trackA, await File.ReadAllBytesAsync(first));
        Assert.Equal(trackB, await File.ReadAllBytesAsync(second));
    }

    [Fact]
    public async Task SameContent_ReusesPath_WhileChangedBytesCreateNewPath()
    {
        byte[] original = [1, 2, 3, 4];
        var first = await ThumbnailStore.SaveAsync(original, _directory);
        var repeated = await ThumbnailStore.SaveAsync(original.ToArray(), _directory);
        var corrected = await ThumbnailStore.SaveAsync([1, 2, 3, 5], _directory);

        Assert.Equal(first, repeated);
        Assert.NotEqual(first, corrected);
        Assert.Equal(original, await File.ReadAllBytesAsync(first));
    }

    [Fact]
    public async Task ConcurrentIdenticalWrites_ReturnOneCompleteFile()
    {
        var bytes = Enumerable.Range(0, 65_536).Select(i => (byte)(i % 251)).ToArray();
        var writes = Enumerable.Range(0, 8).Select(_ => ThumbnailStore.SaveAsync(bytes, _directory));
        var paths = await Task.WhenAll(writes);

        Assert.Single(paths.Distinct());
        Assert.Equal(bytes, await File.ReadAllBytesAsync(paths[0]));
        Assert.Single(Directory.GetFiles(_directory));
    }

    public void Dispose()
    {
        if (Directory.Exists(_directory)) Directory.Delete(_directory, recursive: true);
    }
}
