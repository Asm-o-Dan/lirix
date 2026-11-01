using System.Diagnostics;
using LirixMediaBridge;

namespace LirixMediaBridge.Tests;

public class MeterDemandTests
{
    [Fact]
    public void StartsDisabledAndDisposesWhileWaitingForDemand()
    {
        int samples = 0;
        var meter = new WasapiLoopbackMeter(_ => Interlocked.Increment(ref samples));
        try
        {
            meter.Start();
            Thread.Sleep(100);
            Assert.Equal(0, Volatile.Read(ref samples));
        }
        finally
        {
            var elapsed = Stopwatch.StartNew();
            meter.Dispose();
            Assert.True(elapsed.Elapsed < TimeSpan.FromSeconds(2));
        }
    }
}
