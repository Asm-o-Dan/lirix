using System.Text;
using System.Text.Json;

namespace LirixMediaBridge;

internal static class Program
{
    public static async Task Main(string[] args)
    {
        Console.InputEncoding = Encoding.UTF8;
        Console.OutputEncoding = Encoding.UTF8;

        using var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (_, e) =>
        {
            e.Cancel = true;
            cts.Cancel();
        };

        using var watcher = new GsmtcWatcher();
        await watcher.StartAsync();

        using var meter = new WasapiLoopbackMeter(msg =>
        {
            BridgeOutput.SendJson(msg, BridgeJsonContext.Relaxed.MeterMessage);
        });
        meter.Start();

        // Stdin command read loop
        try
        {
            while (!cts.IsCancellationRequested)
            {
                var readLineTask = Console.In.ReadLineAsync();
                var completedTask = await Task.WhenAny(readLineTask, Task.Delay(Timeout.Infinite, cts.Token));

                if (completedTask != readLineTask)
                {
                    break;
                }

                var line = await readLineTask;
                if (line == null)
                {
                    // Stdin pipe closed by parent process
                    break;
                }

                if (string.IsNullOrWhiteSpace(line)) continue;

                try
                {
                    var cmd = JsonSerializer.Deserialize(line, BridgeJsonContext.Default.CommandMessage);
                    if (cmd != null && !string.IsNullOrEmpty(cmd.Cmd))
                    {
                        await watcher.ExecuteCommandAsync(cmd);
                    }
                }
                catch
                {
                    // Ignore malformed command lines
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Graceful shutdown on Ctrl+C / cancellation
        }
    }
}
