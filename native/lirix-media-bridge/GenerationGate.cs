namespace LirixMediaBridge;

public sealed class GenerationGate
{
    private long _value;
    public long Current => Interlocked.Read(ref _value);
    public long Next() => Interlocked.Increment(ref _value);
    public bool IsCurrent(long value) => Interlocked.Read(ref _value) == value;
}
