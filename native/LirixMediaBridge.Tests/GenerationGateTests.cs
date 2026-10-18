using Xunit;

namespace LirixMediaBridge.Tests;

public class GenerationGateTests
{
    [Fact]
    public async Task LateCompletionFromPreviousGeneration_CannotPublishOverCurrentState()
    {
        var gate = new GenerationGate();
        var oldGeneration = gate.Next();
        var oldResult = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        var published = new List<string>();

        async Task PublishWhenReady(long generation, Task<string> result)
        {
            var value = await result;
            if (gate.IsCurrent(generation)) published.Add(value);
        }

        var oldPublication = PublishWhenReady(oldGeneration, oldResult.Task);
        var currentGeneration = gate.Next();
        await PublishWhenReady(currentGeneration, Task.FromResult("current session image"));
        oldResult.SetResult("previous session image");
        await oldPublication;

        Assert.Equal(new[] { "current session image" }, published);
        Assert.False(gate.IsCurrent(oldGeneration));
        Assert.True(gate.IsCurrent(currentGeneration));
    }
}
